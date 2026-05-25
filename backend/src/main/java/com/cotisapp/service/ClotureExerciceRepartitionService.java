package com.cotisapp.service;

import com.cotisapp.domain.entity.*;
import com.cotisapp.domain.enums.ModeAgregationPostesCloture;
import com.cotisapp.domain.enums.ModeCalculProrataCloture;
import com.cotisapp.domain.enums.ModeRepartitionCloture;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.domain.enums.StatutExercice;
import com.cotisapp.domain.enums.TypeModeCalcul;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.request.ParametrageClotureExerciceRequest;
import com.cotisapp.dto.response.MembreRepartitionClotureResponse;
import com.cotisapp.dto.response.PostePartageClotureResponse;
import com.cotisapp.dto.response.PreviewClotureExerciceResponse;
import com.cotisapp.dto.response.RetenueClotureResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.ExerciceRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.service.cloture.MembrePourcentageRepartitionItem;
import com.cotisapp.security.OrganisationContext;
import com.cotisapp.service.cloture.PostePartageClotureItem;
import com.cotisapp.service.cloture.RetenueClotureItem;
import com.cotisapp.util.PartsCotisationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ClotureExerciceRepartitionService {

    public static final String CODE_POOL_ADDITIONNE = "__POOL__";

    private final ParametrageClotureExerciceService parametrageClotureExerciceService;
    private final EmpruntRepository empruntRepository;
    private final OperationRepository operationRepository;
    private final MembreRepository membreRepository;
    private final CompteService compteService;
    private final ExerciceRepository exerciceRepository;
    private final OrganisationRepository organisationRepository;
    private final JournalService journalService;

    @Transactional(readOnly = true)
    public PreviewClotureExerciceResponse previsualiser(Long orgId, Long exerciceId) {
        Exercice exercice = requireExerciceCourant(orgId);
        if (!exercice.getId().equals(exerciceId)) {
            throw new BusinessException("La prévisualisation concerne uniquement l'exercice en cours");
        }
        return calculerPreview(orgId, exercice, parametrageClotureExerciceService.assurerParametrage(orgId));
    }

    @Transactional(readOnly = true)
    public PreviewClotureExerciceResponse previsualiserDepuisRequest(
            Long orgId, ParametrageClotureExerciceRequest request) {
        Exercice exercice = requireExerciceCourant(orgId);
        ParametrageClotureExercice sim = parametrageClotureExerciceService.simulerDepuisRequest(orgId, request);
        return calculerPreview(orgId, exercice, sim);
    }

    @Transactional
    public PreviewClotureExerciceResponse executerRepartition(Long orgId, Long exerciceId) {
        Exercice exercice = requireExerciceCourant(orgId);
        if (!exercice.getId().equals(exerciceId)) {
            throw new BusinessException("La répartition ne peut être effectuée que sur l'exercice en cours");
        }
        PreviewClotureExerciceResponse preview =
                calculerPreview(orgId, exercice, parametrageClotureExerciceService.assurerParametrage(orgId));
        if (preview.getNetADistribuer().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Aucun montant à distribuer après frais et retenues");
        }
        ModeRepartitionCloture mode = ModeRepartitionCloture.valueOf(preview.getModeRepartition());
        ModeCalculProrataCloture calcProrata = parametrageClotureExerciceService
                .assurerParametrage(orgId)
                .getModeCalculProrata();
        if (mode == ModeRepartitionCloture.PRORATA
                && calcProrata == ModeCalculProrataCloture.PARTS
                && preview.getTotalParts() <= 0) {
            throw new BusinessException(
                    "Aucune part membre calculée — vérifiez les cotisations de l'exercice et l'intervalle de parts");
        }

        ParametrageClotureExercice param = parametrageClotureExerciceService.assurerParametrage(orgId);
        List<PostePartageClotureItem> postesCfg = parametrageClotureExerciceService.lirePostes(param);
        ModeAgregationPostesCloture modeAgreg = param.getModeAgregationPostes() != null
                ? param.getModeAgregationPostes()
                : ModeAgregationPostesCloture.SEPARER;
        List<PostePartageClotureItem> postesExec = postesPourDistribution(
                postesCfg, modeAgreg, param.getCompteVersementMembre());
        BigDecimal totalVerse = BigDecimal.ZERO;

        for (MembreRepartitionClotureResponse ligne : preview.getMembres()) {
            if (ligne.isExcluDuPartage()) {
                continue;
            }
            compteService.ensureComptesMembre(orgId, ligne.getMembreId());
            for (PostePartageClotureItem poste : postesExec) {
                BigDecimal montant = montantPostePourMembre(ligne, poste.code());
                if (montant.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Compte compteOrg = compteService.getCompteOrg(orgId, poste.compteSourceOrg());
                Compte compteMembre = compteService.creerCompteMembre(orgId, ligne.getMembreId(), poste.compteMembre(), null);

                Operation operation = Operation.builder()
                        .organisationId(orgId)
                        .exerciceId(exerciceId)
                        .membreId(ligne.getMembreId())
                        .typeOperation(TypeOperation.REPARTITION_EXERCICE)
                        .montant(montant)
                        .dateOperation(LocalDate.now())
                        .observation("Répartition clôture exercice n°" + exercice.getNumero()
                                + " — " + poste.libelle())
                        .utilisateurId(OrganisationContext.getUserId())
                        .build();

                List<MouvementCompte> mouvements = new ArrayList<>();
                mouvements.add(MouvementCompte.builder()
                        .operation(operation)
                        .compteId(compteOrg.getId())
                        .sens(SensMouvement.DEBIT)
                        .montant(montant)
                        .build());
                mouvements.add(MouvementCompte.builder()
                        .operation(operation)
                        .compteId(compteMembre.getId())
                        .sens(SensMouvement.CREDIT)
                        .montant(montant)
                        .build());
                operation.setMouvements(mouvements);

                compteService.appliquerMouvement(compteOrg.getId(), SensMouvement.DEBIT, montant, false);
                compteService.appliquerMouvement(compteMembre.getId(), SensMouvement.CREDIT, montant, true);
                operationRepository.save(operation);
                totalVerse = totalVerse.add(montant);
            }
        }

        journalService.enregistrer(
                orgId,
                "REPARTITION_CLOTURE",
                "Répartition exercice n°" + exercice.getNumero() + " — " + totalVerse + " FCFA versés aux membres");
        return preview;
    }

    private PreviewClotureExerciceResponse calculerPreview(
            Long orgId, Exercice exercice, ParametrageClotureExercice param) {
        Long exerciceId = exercice.getId();
        ModeRepartitionCloture mode = param.getModeRepartition() != null
                ? param.getModeRepartition()
                : ModeRepartitionCloture.PRORATA;

        List<PostePartageClotureItem> postesCfg = parametrageClotureExerciceService.lirePostes(param);
        List<PostePartageClotureResponse> postesPreview = new ArrayList<>();
        Map<String, BigDecimal> pools = new LinkedHashMap<>();
        BigDecimal poolBrut = BigDecimal.ZERO;
        BigDecimal interets = BigDecimal.ZERO;
        BigDecimal penalites = BigDecimal.ZERO;
        BigDecimal amendes = BigDecimal.ZERO;

        for (PostePartageClotureItem poste : postesCfg) {
            BigDecimal pool = poste.actif() ? calculerPoolPoste(orgId, exerciceId, poste) : BigDecimal.ZERO;
            pools.put(poste.code(), pool);
            poolBrut = poolBrut.add(pool);
            if ("INTERETS".equals(poste.code())) {
                interets = pool;
            } else if ("PENALITES".equals(poste.code())) {
                penalites = pool;
            } else if ("AMENDES".equals(poste.code())) {
                amendes = pool;
            }
            postesPreview.add(PostePartageClotureResponse.builder()
                    .code(poste.code())
                    .libelle(poste.libelle())
                    .actif(poste.actif())
                    .builtIn(poste.builtIn())
                    .compteMembre(poste.compteMembre())
                    .compteSourceOrg(poste.compteSourceOrg())
                    .typeOperation(poste.typeOperation())
                    .groupePartage(poste.groupePartage())
                    .inclureDansPoolAdditionne(poste.inclureDansPoolAdditionne())
                    .appliquerProrata(poste.appliquerProrata())
                    .montantPool(pool)
                    .build());
        }

        BigDecimal frais = calculerFrais(param.getFraisClotureType(), param.getFraisClotureValeur(), poolBrut);
        BigDecimal reste = poolBrut.subtract(frais);
        if (reste.signum() < 0) {
            throw new BusinessException("Les frais de clôture dépassent le montant à répartir");
        }

        List<RetenueClotureItem> retenuesCfg = parametrageClotureExerciceService.lireRetenues(param);
        List<RetenueClotureResponse> retenuesCalc = new ArrayList<>();
        BigDecimal totalRetenues = BigDecimal.ZERO;
        for (RetenueClotureItem item : retenuesCfg) {
            BigDecimal montant = calculerRetenue(item, reste);
            totalRetenues = totalRetenues.add(montant);
            reste = reste.subtract(montant);
            retenuesCalc.add(RetenueClotureResponse.builder()
                    .libelle(item.libelle())
                    .typeMode(item.typeMode())
                    .valeur(item.valeur())
                    .ordre(item.ordre())
                    .montantCalcule(montant)
                    .build());
            if (reste.signum() < 0) {
                throw new BusinessException("Les retenues dépassent le montant disponible après frais");
            }
        }

        BigDecimal net = reste;
        ModeAgregationPostesCloture modeAgreg = param.getModeAgregationPostes() != null
                ? param.getModeAgregationPostes()
                : ModeAgregationPostesCloture.SEPARER;
        Map<String, BigDecimal> netsParPoste =
                repartirNetSelonAgregation(pools, poolBrut, net, postesCfg, modeAgreg, param.getCompteVersementMembre());

        Map<Long, CotisationMembreAgg> cotisations = chargerCotisations(orgId, exerciceId);
        Map<Long, Membre> membres = new HashMap<>();
        membreRepository.findByOrganisationIdAndActifTrue(orgId).forEach(m -> membres.put(m.getId(), m));

        List<MembrePourcentageRepartitionItem> pourcentagesCfg = parametrageClotureExerciceService.lirePourcentages(param);
        List<MembreLigne> lignes = construireLignesMembres(
                param, cotisations, membres, mode, orgId, pourcentagesCfg);
        List<PostePartageClotureItem> postesDistribution =
                postesPourDistribution(postesCfg, modeAgreg, param.getCompteVersementMembre());
        distribuerParPostes(lignes, netsParPoste, postesDistribution, mode, param.getModeCalculProrata());

        for (PostePartageClotureResponse p : postesPreview) {
            if (modeAgreg == ModeAgregationPostesCloture.ADDITIONNER) {
                if (p.isInclureDansPoolAdditionne()) {
                    p.setMontantDistribue(netsParPoste.getOrDefault(CODE_POOL_ADDITIONNE, BigDecimal.ZERO));
                } else {
                    p.setMontantDistribue(netsParPoste.getOrDefault(p.getCode(), BigDecimal.ZERO));
                }
            } else if (modeAgreg == ModeAgregationPostesCloture.GROUPES) {
                p.setMontantDistribue(netsParPoste.getOrDefault(cleGroupe(p.getGroupePartage()), BigDecimal.ZERO));
            } else {
                p.setMontantDistribue(netsParPoste.getOrDefault(p.getCode(), BigDecimal.ZERO));
            }
        }

        List<MembreRepartitionClotureResponse> membresResponse = lignes.stream()
                .map(this::toMembreResponse)
                .sorted(Comparator.comparing(MembreRepartitionClotureResponse::getNomComplet, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int totalParts = lignes.stream().filter(l -> !l.exclu).mapToInt(l -> l.parts).sum();

        return PreviewClotureExerciceResponse.builder()
                .exerciceId(exerciceId)
                .exerciceNumero(exercice.getNumero())
                .poolInterets(interets)
                .poolPenalites(penalites)
                .poolAmendes(amendes)
                .modeRepartition(mode.name())
                .modeAgregationPostes(modeAgreg.name())
                .modeCalculProrata(
                        param.getModeCalculProrata() != null
                                ? param.getModeCalculProrata().name()
                                : ModeCalculProrataCloture.PARTS.name())
                .exclureMembresPretEnCours(Boolean.TRUE.equals(param.getExclureMembresPretEnCours()))
                .postes(postesPreview)
                .poolBrut(poolBrut)
                .fraisCloture(frais)
                .retenues(retenuesCalc)
                .totalRetenues(totalRetenues)
                .netADistribuer(net)
                .totalParts(totalParts)
                .membres(membresResponse)
                .build();
    }

    private BigDecimal calculerPoolPoste(Long orgId, Long exerciceId, PostePartageClotureItem poste) {
        if (poste.builtIn()) {
            return switch (poste.code()) {
                case "INTERETS" -> nz(operationRepository.sumFraisEmpruntByExercice(orgId, exerciceId));
                case "PENALITES" -> nz(operationRepository.sumPenalitesRemboursementByExercice(orgId, exerciceId));
                case "AMENDES" -> nz(operationRepository.sumAmendesCotisationByExercice(orgId, exerciceId));
                default -> BigDecimal.ZERO;
            };
        }
        if (poste.typeOperation() == null) {
            return BigDecimal.ZERO;
        }
        return nz(operationRepository.sumMontantByTypeOperationExercice(orgId, exerciceId, poste.typeOperation()));
    }

    private Map<String, BigDecimal> repartirNetSelonAgregation(
            Map<String, BigDecimal> pools,
            BigDecimal poolBrut,
            BigDecimal net,
            List<PostePartageClotureItem> postesCfg,
            ModeAgregationPostesCloture modeAgreg,
            com.cotisapp.domain.enums.TypeCompte compteVersementPool) {
        if (modeAgreg == ModeAgregationPostesCloture.ADDITIONNER) {
            Map<String, BigDecimal> poolsEffectifs = construirePoolsAdditionSelective(pools, postesCfg);
            List<PostePartageClotureItem> cles =
                    construirePostesClesAddition(postesCfg, compteVersementPool);
            return repartirNetParPoste(poolsEffectifs, poolBrut, net, cles);
        }
        if (modeAgreg == ModeAgregationPostesCloture.GROUPES) {
            Map<String, BigDecimal> poolsGroupes = new LinkedHashMap<>();
            for (PostePartageClotureItem poste : postesCfg) {
                if (!poste.actif()) {
                    continue;
                }
                String cle = cleGroupe(poste.groupePartage());
                poolsGroupes.merge(cle, pools.getOrDefault(poste.code(), BigDecimal.ZERO), BigDecimal::add);
            }
            List<PostePartageClotureItem> postesGroupes = new ArrayList<>();
            for (int g : List.of(1, 2)) {
                if (poolsGroupes.containsKey(cleGroupe(g))) {
                    PostePartageClotureItem ref = postesCfg.stream()
                            .filter(p -> p.actif() && Objects.equals(p.groupePartage(), g))
                            .findFirst()
                            .orElse(null);
                    if (ref != null) {
                        postesGroupes.add(new PostePartageClotureItem(
                                cleGroupe(g),
                                "Groupe " + g,
                                true,
                                false,
                                ref.compteMembre(),
                                ref.compteSourceOrg(),
                                null,
                                g,
                                false,
                                ref.appliquerProrata()));
                    }
                }
            }
            return repartirNetParPoste(poolsGroupes, poolBrut, net, postesGroupes);
        }
        return repartirNetParPoste(pools, poolBrut, net, postesCfg);
    }

    private static String cleGroupe(Integer groupe) {
        int g = groupe != null && groupe == 2 ? 2 : 1;
        return "GROUPE_" + g;
    }

    private Map<String, BigDecimal> construirePoolsAdditionSelective(
            Map<String, BigDecimal> pools, List<PostePartageClotureItem> postesCfg) {
        Map<String, BigDecimal> eff = new LinkedHashMap<>();
        BigDecimal combine = BigDecimal.ZERO;
        for (PostePartageClotureItem poste : postesCfg) {
            if (!poste.actif()) {
                continue;
            }
            BigDecimal montant = pools.getOrDefault(poste.code(), BigDecimal.ZERO);
            if (poste.inclureDansPoolAdditionne()) {
                combine = combine.add(montant);
            } else {
                eff.put(poste.code(), montant);
            }
        }
        if (combine.signum() > 0) {
            eff.put(CODE_POOL_ADDITIONNE, combine);
        }
        return eff;
    }

    private List<PostePartageClotureItem> construirePostesClesAddition(
            List<PostePartageClotureItem> postesCfg,
            com.cotisapp.domain.enums.TypeCompte compteVersementPool) {
        List<PostePartageClotureItem> cles = new ArrayList<>();
        PostePartageClotureItem refPool = null;
        for (PostePartageClotureItem p : postesCfg) {
            if (!p.actif()) {
                continue;
            }
            if (p.inclureDansPoolAdditionne()) {
                if (refPool == null) {
                    refPool = p;
                }
            } else {
                cles.add(p);
            }
        }
        if (refPool != null) {
            cles.add(new PostePartageClotureItem(
                    CODE_POOL_ADDITIONNE,
                    "Montants additionnés",
                    true,
                    false,
                    compteVersementPool != null ? compteVersementPool : refPool.compteMembre(),
                    refPool.compteSourceOrg(),
                    null,
                    null,
                    true,
                    refPool.appliquerProrata()));
        }
        return cles;
    }

    private List<PostePartageClotureItem> postesPourDistribution(
            List<PostePartageClotureItem> postesCfg,
            ModeAgregationPostesCloture modeAgreg,
            com.cotisapp.domain.enums.TypeCompte compteVersementPool) {
        if (modeAgreg == ModeAgregationPostesCloture.ADDITIONNER) {
            return construirePostesClesAddition(postesCfg, compteVersementPool);
        }
        if (modeAgreg == ModeAgregationPostesCloture.GROUPES) {
            List<PostePartageClotureItem> groupes = new ArrayList<>();
            for (int g : List.of(1, 2)) {
                final int groupe = g;
                PostePartageClotureItem premier = postesCfg.stream()
                        .filter(p -> p.actif() && Objects.equals(p.groupePartage(), groupe))
                        .findFirst()
                        .orElse(null);
                if (premier != null) {
                    groupes.add(new PostePartageClotureItem(
                            cleGroupe(groupe),
                            "Groupe " + groupe,
                            true,
                            false,
                            premier.compteMembre(),
                            premier.compteSourceOrg(),
                            null,
                            groupe,
                            false,
                            premier.appliquerProrata()));
                }
            }
            return groupes;
        }
        return postesCfg.stream().filter(PostePartageClotureItem::actif).toList();
    }

    private Map<String, BigDecimal> repartirNetParPoste(
            Map<String, BigDecimal> pools,
            BigDecimal poolBrut,
            BigDecimal net,
            List<PostePartageClotureItem> postesCfg) {
        Map<String, BigDecimal> nets = new LinkedHashMap<>();
        if (net.signum() <= 0 || poolBrut.signum() <= 0) {
            for (PostePartageClotureItem p : postesCfg) {
                if (p.actif()) {
                    nets.put(p.code(), BigDecimal.ZERO);
                }
            }
            return nets;
        }
        BigDecimal reste = net;
        List<PostePartageClotureItem> actifs = postesCfg.stream().filter(PostePartageClotureItem::actif).toList();
        for (int i = 0; i < actifs.size(); i++) {
            PostePartageClotureItem poste = actifs.get(i);
            BigDecimal pool = pools.getOrDefault(poste.code(), BigDecimal.ZERO);
            BigDecimal partPoste;
            if (i == actifs.size() - 1) {
                partPoste = reste;
            } else {
                partPoste = net.multiply(pool).divide(poolBrut, 0, RoundingMode.DOWN);
                reste = reste.subtract(partPoste);
            }
            nets.put(poste.code(), partPoste);
        }
        return nets;
    }

    private List<MembreLigne> construireLignesMembres(
            ParametrageClotureExercice param,
            Map<Long, CotisationMembreAgg> cotisations,
            Map<Long, Membre> membres,
            ModeRepartitionCloture mode,
            Long orgId,
            List<MembrePourcentageRepartitionItem> pourcentagesCfg) {
        boolean exclurePret = Boolean.TRUE.equals(param.getExclureMembresPretEnCours());
        ModeCalculProrataCloture calcProrata = param.getModeCalculProrata() != null
                ? param.getModeCalculProrata()
                : ModeCalculProrataCloture.PARTS;
        List<MembreLigne> lignes = new ArrayList<>();
        if (mode == ModeRepartitionCloture.EQUITABLE) {
            for (Membre m : membres.values()) {
                boolean exclu = exclurePret && aPretEnCours(orgId, m.getId());
                CotisationMembreAgg agg = cotisations.getOrDefault(m.getId(), new CotisationMembreAgg(BigDecimal.ZERO, 0));
                lignes.add(new MembreLigne(
                        m.getId(),
                        m.getCodeMembre(),
                        m.getNomComplet(),
                        exclu ? 0 : 1,
                        null,
                        exclu,
                        exclu ? "Prêt en cours" : null,
                        agg.total,
                        new LinkedHashMap<>()));
            }
            return lignes;
        }

        if (calcProrata == ModeCalculProrataCloture.POURCENTAGE) {
            for (MembrePourcentageRepartitionItem item : pourcentagesCfg) {
                Membre m = membres.get(item.membreId());
                if (m == null) {
                    continue;
                }
                boolean exclu = exclurePret && aPretEnCours(orgId, m.getId());
                int poids = exclu ? 0 : item.pourcentage().multiply(new BigDecimal("100")).intValue();
                CotisationMembreAgg agg = cotisations.getOrDefault(m.getId(), new CotisationMembreAgg(BigDecimal.ZERO, 0));
                lignes.add(new MembreLigne(
                        m.getId(),
                        m.getCodeMembre(),
                        m.getNomComplet(),
                        poids,
                        item.pourcentage(),
                        exclu,
                        exclu ? "Prêt en cours" : null,
                        agg.total,
                        new LinkedHashMap<>()));
            }
            return lignes;
        }

        for (Map.Entry<Long, CotisationMembreAgg> e : cotisations.entrySet()) {
            Long membreId = e.getKey();
            CotisationMembreAgg agg = e.getValue();
            int parts = PartsCotisationUtil.calculerParts(
                    agg.montantMoyen(),
                    param.getCotisationMontantMin(),
                    param.getCotisationMontantMax(),
                    param.getPartsMin(),
                    param.getPartsMax());
            if (parts <= 0) {
                continue;
            }
            Membre m = membres.get(membreId);
            boolean exclu = exclurePret && aPretEnCours(orgId, membreId);
            lignes.add(new MembreLigne(
                    membreId,
                    m != null ? m.getCodeMembre() : "",
                    m != null ? m.getNomComplet() : "Membre #" + membreId,
                    exclu ? 0 : parts,
                    null,
                    exclu,
                    exclu ? "Prêt en cours" : null,
                    agg.total,
                    new LinkedHashMap<>()));
        }
        return lignes;
    }

    private boolean aPretEnCours(Long orgId, Long membreId) {
        return empruntRepository.existsByMembreIdAndOrganisationIdAndStatut(membreId, orgId, StatutEmprunt.EN_COURS);
    }

    private void distribuerParPostes(
            List<MembreLigne> lignes,
            Map<String, BigDecimal> netsParPoste,
            List<PostePartageClotureItem> postesCfg,
            ModeRepartitionCloture mode,
            ModeCalculProrataCloture calcProrata) {
        List<MembreLigne> eligibles = lignes.stream().filter(l -> !l.exclu).toList();
        for (PostePartageClotureItem poste : postesCfg) {
            if (!poste.actif()) {
                continue;
            }
            BigDecimal montantPoste = netsParPoste.getOrDefault(poste.code(), BigDecimal.ZERO);
            if (montantPoste.signum() <= 0 || eligibles.isEmpty()) {
                continue;
            }
            boolean prorataSurPoste = mode == ModeRepartitionCloture.PRORATA && poste.appliquerProrata();
            if (prorataSurPoste) {
                repartirProrata(eligibles, poste.code(), montantPoste);
            } else {
                repartirEquitable(eligibles, poste.code(), montantPoste);
            }
        }
        for (MembreLigne ligne : lignes) {
            ligne.montantTotal = ligne.montantsParPoste.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private void repartirProrata(List<MembreLigne> lignes, String codePoste, BigDecimal montantPoste) {
        int totalPoids = lignes.stream().mapToInt(l -> l.parts).sum();
        if (totalPoids <= 0) {
            return;
        }
        BigDecimal reste = montantPoste;
        for (int i = 0; i < lignes.size(); i++) {
            MembreLigne ligne = lignes.get(i);
            BigDecimal part;
            if (i == lignes.size() - 1) {
                part = reste;
            } else {
                part = montantPoste.multiply(BigDecimal.valueOf(ligne.parts))
                        .divide(BigDecimal.valueOf(totalPoids), 0, RoundingMode.DOWN);
                reste = reste.subtract(part);
            }
            ligne.montantsParPoste.merge(codePoste, part, BigDecimal::add);
        }
    }

    private void repartirEquitable(List<MembreLigne> lignes, String codePoste, BigDecimal montantPoste) {
        int n = lignes.size();
        if (n <= 0) {
            return;
        }
        BigDecimal reste = montantPoste;
        for (int i = 0; i < lignes.size(); i++) {
            BigDecimal part;
            if (i == lignes.size() - 1) {
                part = reste;
            } else {
                part = montantPoste.divide(BigDecimal.valueOf(n), 0, RoundingMode.DOWN);
                reste = reste.subtract(part);
            }
            lignes.get(i).montantsParPoste.merge(codePoste, part, BigDecimal::add);
        }
    }

    private MembreRepartitionClotureResponse toMembreResponse(MembreLigne ligne) {
        return MembreRepartitionClotureResponse.builder()
                .membreId(ligne.membreId)
                .codeMembre(ligne.codeMembre)
                .nomComplet(ligne.nomComplet)
                .nombreParts(ligne.parts)
                .pourcentageRepartition(ligne.pourcentage)
                .excluDuPartage(ligne.exclu)
                .motifExclusion(ligne.motifExclusion)
                .montantCotisationsExercice(ligne.cotisationsTotal)
                .montantPart(ligne.montantTotal)
                .montantInterets(ligne.montantsParPoste.getOrDefault("INTERETS", BigDecimal.ZERO))
                .montantPenalites(ligne.montantsParPoste.getOrDefault("PENALITES", BigDecimal.ZERO))
                .montantAmendes(ligne.montantsParPoste.getOrDefault("AMENDES", BigDecimal.ZERO))
                .montantsParPoste(new LinkedHashMap<>(ligne.montantsParPoste))
                .build();
    }

    private static BigDecimal montantPostePourMembre(MembreRepartitionClotureResponse ligne, String code) {
        if (ligne.getMontantsParPoste() != null && ligne.getMontantsParPoste().containsKey(code)) {
            return nz(ligne.getMontantsParPoste().get(code));
        }
        return switch (code) {
            case "INTERETS" -> nz(ligne.getMontantInterets());
            case "PENALITES" -> nz(ligne.getMontantPenalites());
            case "AMENDES" -> nz(ligne.getMontantAmendes());
            default -> BigDecimal.ZERO;
        };
    }

    private Map<Long, CotisationMembreAgg> chargerCotisations(Long orgId, Long exerciceId) {
        Map<Long, CotisationMembreAgg> map = new HashMap<>();
        for (Object[] row : operationRepository.sumCotisationsParMembreExercice(orgId, exerciceId)) {
            Long membreId = (Long) row[0];
            BigDecimal total = (BigDecimal) row[1];
            long count = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            map.put(membreId, new CotisationMembreAgg(total, count));
        }
        return map;
    }

    private BigDecimal calculerFrais(TypeModeCalcul type, BigDecimal valeur, BigDecimal base) {
        if (valeur == null || valeur.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (type == TypeModeCalcul.POURCENTAGE) {
            return base.multiply(valeur).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        return valeur;
    }

    private BigDecimal calculerRetenue(RetenueClotureItem item, BigDecimal baseRestante) {
        if (item.valeur() == null || item.valeur().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (item.typeMode() == TypeModeCalcul.POURCENTAGE) {
            return baseRestante.multiply(item.valeur()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        return item.valeur().min(baseRestante);
    }

    private Exercice requireExerciceCourant(Long orgId) {
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new BusinessException("Organisation introuvable"));
        if (org.getExerciceCourantId() == null) {
            throw new BusinessException("Aucun exercice en cours pour cette organisation");
        }
        Exercice exercice = exerciceRepository
                .findByIdAndOrganisationId(org.getExerciceCourantId(), orgId)
                .orElseThrow(() -> new BusinessException("Exercice courant introuvable"));
        if (exercice.getStatut() != StatutExercice.EN_COURS) {
            throw new BusinessException("L'exercice courant n'est plus actif");
        }
        return exercice;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private record CotisationMembreAgg(BigDecimal total, long count) {
        BigDecimal montantMoyen() {
            if (count <= 0) {
                return BigDecimal.ZERO;
            }
            return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }
    }

    private static class MembreLigne {
        final Long membreId;
        final String codeMembre;
        final String nomComplet;
        final int parts;
        final BigDecimal pourcentage;
        final boolean exclu;
        final String motifExclusion;
        final BigDecimal cotisationsTotal;
        final Map<String, BigDecimal> montantsParPoste;
        BigDecimal montantTotal = BigDecimal.ZERO;

        MembreLigne(
                Long membreId,
                String codeMembre,
                String nomComplet,
                int parts,
                BigDecimal pourcentage,
                boolean exclu,
                String motifExclusion,
                BigDecimal cotisationsTotal,
                Map<String, BigDecimal> montantsParPoste) {
            this.membreId = membreId;
            this.codeMembre = codeMembre;
            this.nomComplet = nomComplet;
            this.parts = parts;
            this.pourcentage = pourcentage;
            this.exclu = exclu;
            this.motifExclusion = motifExclusion;
            this.cotisationsTotal = cotisationsTotal;
            this.montantsParPoste = montantsParPoste;
        }
    }
}
