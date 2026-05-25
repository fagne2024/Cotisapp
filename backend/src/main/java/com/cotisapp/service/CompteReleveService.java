package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.MouvementCompte;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.response.*;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.MouvementCompteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompteReleveService {

    private static final DateTimeFormatter GROUPE_DATE =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);

    private final CompteRepository compteRepository;
    private final MouvementCompteRepository mouvementCompteRepository;
    private final MembreRepository membreRepository;
    private final MembreFicheService membreFicheService;
    private final EmpruntRepository empruntRepository;
    private final CompteService compteService;

    @Transactional(readOnly = true)
    public CompteReleveSyntheseResponse chargerSynthese(Long orgId) {
        List<CompteOrgCardResponse> orgCards = new ArrayList<>();
        BigDecimal totalActifs = BigDecimal.ZERO;
        BigDecimal variationJour = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();

        for (TypeCompte type : List.of(TypeCompte.CAISSE, TypeCompte.BANQUE, TypeCompte.SOLIDARITE, TypeCompte.INTERET)) {
            Optional<Compte> opt = compteRepository.findByOrganisationIdAndTypeCompteAndProprietaire(
                    orgId, type, ProprietaireCompte.ORGANISATION);
            if (opt.isEmpty()) {
                continue;
            }
            Compte c = opt.get();
            BigDecimal solde = nz(c.getSolde());
            BigDecimal varJour = nz(mouvementCompteRepository.sumVariationComptePourDate(orgId, c.getId(), today));
            totalActifs = totalActifs.add(solde);
            variationJour = variationJour.add(varJour);
            orgCards.add(CompteOrgCardResponse.builder()
                    .compteId(c.getId())
                    .typeCompte(type)
                    .libelle(libelleOrg(type, c))
                    .sousTitre(sousTitreOrg(type))
                    .solde(solde)
                    .variationJour(varJour)
                    .icone(iconeOrg(type))
                    .build());
        }

        CompteOrgCardResponse carteAmendes = construireCarteAmendesPenalitesAgregee(orgId, today);
        orgCards.add(carteAmendes);
        totalActifs = totalActifs.add(nz(carteAmendes.getSolde()));
        variationJour = variationJour.add(nz(carteAmendes.getVariationJour()));

        BigDecimal encours =
                nz(empruntRepository.sumEncoursByOrganisationIdAndStatut(orgId, StatutEmprunt.EN_COURS));
        long nbEmpruntsEnCours =
                empruntRepository.countByOrganisationIdAndStatut(orgId, StatutEmprunt.EN_COURS);

        Map<Long, Membre> membresMap = membreRepository.findByOrganisationId(orgId).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));
        List<MembreSoldesResponse> soldes = membreFicheService.listerSoldesComptes(orgId);
        List<CompteMembreResumeResponse> membres = soldes.stream()
                .map(s -> toMembreResume(s, membresMap.get(s.getMembreId())))
                .sorted(Comparator.comparing(CompteMembreResumeResponse::getNomComplet, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return CompteReleveSyntheseResponse.builder()
                .comptesOrganisation(orgCards)
                .totalActifs(totalActifs)
                .encoursEmprunts(encours)
                .nbEmpruntsEnCours(nbEmpruntsEnCours)
                .variationJourGlobale(variationJour)
                .membres(membres)
                .build();
    }

    @Transactional(readOnly = true)
    public CompteReleveResponse chargerReleve(
            Long orgId,
            String scope,
            Long compteId,
            Long membreId,
            LocalDate dateDebut,
            LocalDate dateFin,
            String typeFiltre,
            String statutFiltre,
            String recherche) {

        LocalDate fin = dateFin != null ? dateFin : LocalDate.now();
        LocalDate debut = dateDebut != null ? dateDebut : fin.withDayOfMonth(1);
        if (debut.isAfter(fin)) {
            throw new BusinessException("La date de début doit être antérieure à la date de fin");
        }

        if ("membre".equalsIgnoreCase(scope)) {
            if (membreId == null) {
                throw new BusinessException("Membre requis pour le relevé membre");
            }
            return releveMembre(orgId, membreId, debut, fin, typeFiltre, statutFiltre, recherche);
        }

        if ("amendes".equalsIgnoreCase(scope)) {
            return releveAmendesPenalites(orgId, debut, fin, typeFiltre, statutFiltre, recherche);
        }

        TypeCompte type = typeCompteFromScope(scope, compteId);
        Compte compte = resolveCompteOrg(orgId, type, compteId);
        return releveOrganisation(orgId, compte, debut, fin, typeFiltre, statutFiltre, recherche);
    }

    private CompteReleveResponse releveOrganisation(
            Long orgId,
            Compte compte,
            LocalDate debut,
            LocalDate fin,
            String typeFiltre,
            String statutFiltre,
            String recherche) {

        BigDecimal solde = nz(compte.getSolde());
        LocalDate today = LocalDate.now();

        Map<Long, Membre> membres = chargerMembresMap(orgId);
        BigDecimal soldeFinPeriode = soldeFinPeriodeOrg(orgId, compte.getId(), solde, fin);
        List<MouvementCompte> raw = mouvementCompteRepository.findByOrganisationAndCompteBetween(
                orgId, compte.getId(), debut, fin);

        List<ReleveLigneResponse> lignes =
                construireLignes(raw, soldeFinPeriode, typeFiltre, statutFiltre, recherche, membres, false);
        ReleveTotauxResponse totaux = calculerTotaux(lignes);

        FluxCaisseSolidariteResponse fluxDetail = null;
        BigDecimal soldeAffiche = solde;
        BigDecimal variationJourAffiche =
                nz(mouvementCompteRepository.sumVariationComptePourDate(orgId, compte.getId(), today));
        BigDecimal entreesMois;
        BigDecimal sortiesMois;

        if (compte.getTypeCompte() == TypeCompte.CAISSE || compte.getTypeCompte() == TypeCompte.SOLIDARITE) {
            fluxDetail = construireFluxCaisseSolidarite(orgId, debut, fin);
            soldeAffiche = nz(fluxDetail.getSoldeCaisse()).add(nz(fluxDetail.getSoldeSolidarite()));
            variationJourAffiche = variationJourCaisseEtSolidarite(orgId, today);
            entreesMois = nz(fluxDetail.getEntreesCaisseMois()).add(nz(fluxDetail.getEntreesSolidariteMois()));
            sortiesMois = nz(fluxDetail.getSortiesCaisseMois()).add(nz(fluxDetail.getSortiesSolidariteMois()));
        } else {
            BigDecimal[] fluxMois = entreesSortiesPeriode(orgId, compte.getId(), debut, fin);
            entreesMois = fluxMois[0];
            sortiesMois = fluxMois[1];
        }

        return CompteReleveResponse.builder()
                .scope(compte.getTypeCompte().name().toLowerCase())
                .compteId(compte.getId())
                .titre(libelleOrg(compte.getTypeCompte(), compte))
                .meta("Compte organisation · Mis à jour le " + formatDateFr(today))
                .icone(iconeOrg(compte.getTypeCompte()))
                .iconeBg(couleurFondOrg(compte.getTypeCompte()))
                .soldeActuel(soldeAffiche)
                .variationJour(variationJourAffiche)
                .entreesMois(entreesMois)
                .sortiesMois(sortiesMois)
                .variationMois(entreesMois.subtract(sortiesMois))
                .fluxCaisseSolidarite(fluxDetail)
                .dateDebut(debut)
                .dateFin(fin)
                .groupes(grouperParDate(lignes))
                .totaux(totaux)
                .build();
    }

    private CompteOrgCardResponse construireCarteAmendesPenalitesAgregee(Long orgId, LocalDate today) {
        List<Long> compteIds = idsComptesAmendesPenalites(orgId);
        BigDecimal solde = BigDecimal.ZERO;
        for (Long id : compteIds) {
            solde = solde.add(nz(compteRepository.findById(id).map(Compte::getSolde).orElse(BigDecimal.ZERO)));
        }
        BigDecimal varJour = compteIds.isEmpty()
                ? BigDecimal.ZERO
                : nz(mouvementCompteRepository.sumVariationComptesPourDate(orgId, compteIds, today));
        return CompteOrgCardResponse.builder()
                .compteId(compteIds.isEmpty() ? null : compteIds.get(0))
                .typeCompte(TypeCompte.AMENDES)
                .libelle("Amendes & pénalités")
                .sousTitre("Fiches membres + encaissements org.")
                .solde(solde)
                .variationJour(varJour)
                .icone("⚖")
                .build();
    }

    private List<Long> idsComptesAmendesPenalites(Long orgId) {
        List<Long> ids = new ArrayList<>();
        compteRepository
                .findByOrganisationIdAndTypeCompteAndProprietaire(orgId, TypeCompte.AMENDES, ProprietaireCompte.ORGANISATION)
                .ifPresent(c -> ids.add(c.getId()));
        compteRepository.findByOrganisationId(orgId).stream()
                .filter(c -> c.getProprietaire() == ProprietaireCompte.MEMBRE)
                .filter(c -> c.getTypeCompte() == TypeCompte.PENALITE || c.getTypeCompte() == TypeCompte.AMENDE)
                .map(Compte::getId)
                .forEach(ids::add);
        return ids;
    }

    private CompteReleveResponse releveAmendesPenalites(
            Long orgId,
            LocalDate debut,
            LocalDate fin,
            String typeFiltre,
            String statutFiltre,
            String recherche) {
        List<Long> compteIds = idsComptesAmendesPenalites(orgId);
        if (compteIds.isEmpty()) {
            return CompteReleveResponse.builder()
                    .scope("amendes")
                    .titre("Amendes & pénalités")
                    .meta("Aucun compte amende / pénalité")
                    .icone("⚖")
                    .iconeBg("var(--re2)")
                    .soldeActuel(BigDecimal.ZERO)
                    .variationJour(BigDecimal.ZERO)
                    .entreesMois(BigDecimal.ZERO)
                    .sortiesMois(BigDecimal.ZERO)
                    .variationMois(BigDecimal.ZERO)
                    .dateDebut(debut)
                    .dateFin(fin)
                    .groupes(List.of())
                    .totaux(ReleveTotauxResponse.builder()
                            .entrees(BigDecimal.ZERO)
                            .sorties(BigDecimal.ZERO)
                            .variationNette(BigDecimal.ZERO)
                            .nbOperations(0)
                            .nbAnnulees(0)
                            .build())
                    .build();
        }
        BigDecimal soldeRef = compteIds.stream()
                .map(id -> nz(compteRepository.findById(id).map(Compte::getSolde).orElse(BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, Membre> membres = chargerMembresMap(orgId);
        BigDecimal soldeFinPeriode = soldeFinPeriodeMembre(orgId, compteIds, soldeRef, fin);
        List<MouvementCompte> raw = mouvementCompteRepository.findByOrganisationAndComptesInBetween(orgId, compteIds, debut, fin);
        List<ReleveLigneResponse> lignes =
                construireLignes(raw, soldeFinPeriode, typeFiltre, statutFiltre, recherche, membres, false);
        return CompteReleveResponse.builder()
                .scope("amendes")
                .titre("Amendes & pénalités")
                .meta(compteIds.size() + " compte(s) · amendes et pénalités membres")
                .icone("⚖")
                .iconeBg("var(--re2)")
                .soldeActuel(soldeRef)
                .variationJour(nz(mouvementCompteRepository.sumVariationComptesPourDate(orgId, compteIds, LocalDate.now())))
                .entreesMois(soldeRef)
                .sortiesMois(BigDecimal.ZERO)
                .variationMois(BigDecimal.ZERO)
                .dateDebut(debut)
                .dateFin(fin)
                .groupes(grouperParDate(lignes))
                .totaux(calculerTotaux(lignes))
                .build();
    }

    private CompteReleveResponse releveMembre(
            Long orgId,
            Long membreId,
            LocalDate debut,
            LocalDate fin,
            String typeFiltre,
            String statutFiltre,
            String recherche) {

        Membre membre = membreRepository
                .findByIdAndOrganisationId(membreId, orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));

        List<Compte> comptes = compteRepository.findByMembreId(membreId).stream()
                .filter(c -> c.getProprietaire() == ProprietaireCompte.MEMBRE)
                .filter(c -> c.getTypeCompte() != TypeCompte.CUSTOM)
                .toList();
        List<Long> compteIds = comptes.stream().map(Compte::getId).toList();
        BigDecimal soldeEpargne = comptes.stream()
                .filter(c -> c.getTypeCompte() == TypeCompte.EPARGNE_HEBDO
                        || c.getTypeCompte() == TypeCompte.EPARGNE_MOIS
                        || c.getTypeCompte() == TypeCompte.EPARGNE)
                .map(c -> nz(c.getSolde()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal soldeSolidarite = comptes.stream()
                .filter(c -> c.getTypeCompte() == TypeCompte.SOLIDARITE)
                .map(c -> nz(c.getSolde()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal soldeDepense = comptes.stream()
                .filter(c -> c.getTypeCompte() == TypeCompte.DEPENSE)
                .map(c -> nz(c.getSolde()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal soldePenalite = comptes.stream()
                .filter(c -> c.getTypeCompte() == TypeCompte.PENALITE)
                .map(c -> nz(c.getSolde()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal soldeAmende = comptes.stream()
                .filter(c -> c.getTypeCompte() == TypeCompte.AMENDE)
                .map(c -> nz(c.getSolde()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, Membre> membres = chargerMembresMap(orgId);
        BigDecimal soldeRef = soldeEpargne.add(soldeSolidarite);
        BigDecimal soldeFinPeriode = soldeFinPeriodeMembre(orgId, compteIds, soldeRef, fin);
        List<MouvementCompte> raw = compteIds.isEmpty()
                ? List.of()
                : mouvementCompteRepository.findByOrganisationAndComptesInBetween(orgId, compteIds, debut, fin);

        List<ReleveLigneResponse> lignes =
                construireLignes(raw, soldeFinPeriode, typeFiltre, statutFiltre, recherche, membres, true);

        return CompteReleveResponse.builder()
                .scope("membre")
                .membreId(membreId)
                .titre(membre.getNomComplet() + " — " + posteLabel(membre.getPoste()))
                .meta(membre.getCodeMembre() + " · " + comptes.size() + " compte(s) membre")
                .icone(initials(membre.getNomComplet()))
                .iconeBg(couleurAvatar(membre.getCodeMembre()))
                .soldeActuel(soldeEpargne)
                .soldeSolidarite(soldeSolidarite)
                .soldeDepense(soldeDepense)
                .soldePenalitesAmendes(soldePenalite.add(soldeAmende))
                .variationJour(BigDecimal.ZERO)
                .entreesMois(BigDecimal.ZERO)
                .sortiesMois(BigDecimal.ZERO)
                .variationMois(BigDecimal.ZERO)
                .dateDebut(debut)
                .dateFin(fin)
                .groupes(grouperParDate(lignes))
                .totaux(calculerTotaux(lignes))
                .build();
    }

    private Map<Long, Membre> chargerMembresMap(Long orgId) {
        return membreRepository.findByOrganisationId(orgId).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));
    }

    private BigDecimal soldeFinPeriodeOrg(Long orgId, Long compteId, BigDecimal soldeActuel, LocalDate fin) {
        BigDecimal apresFin = nz(mouvementCompteRepository.sumVariationCompteApresDate(orgId, compteId, fin));
        return nz(soldeActuel).subtract(apresFin);
    }

    private BigDecimal soldeFinPeriodeMembre(Long orgId, List<Long> compteIds, BigDecimal soldeRef, LocalDate fin) {
        if (compteIds == null || compteIds.isEmpty()) {
            return nz(soldeRef);
        }
        BigDecimal apresFin = nz(mouvementCompteRepository.sumVariationComptesApresDate(orgId, compteIds, fin));
        return nz(soldeRef).subtract(apresFin);
    }

    private FluxCaisseSolidariteResponse construireFluxCaisseSolidarite(
            Long orgId, LocalDate fluxDebut, LocalDate fluxFin) {
        Optional<Compte> caisseOpt = compteRepository.findByOrganisationIdAndTypeCompteAndProprietaire(
                orgId, TypeCompte.CAISSE, ProprietaireCompte.ORGANISATION);
        Optional<Compte> solOpt = compteRepository.findByOrganisationIdAndTypeCompteAndProprietaire(
                orgId, TypeCompte.SOLIDARITE, ProprietaireCompte.ORGANISATION);

        BigDecimal[] fluxCaisse = caisseOpt
                .map(c -> entreesSortiesPeriode(orgId, c.getId(), fluxDebut, fluxFin))
                .orElse(new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        BigDecimal[] fluxSol = solOpt
                .map(c -> entreesSortiesPeriode(orgId, c.getId(), fluxDebut, fluxFin))
                .orElse(new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});

        return FluxCaisseSolidariteResponse.builder()
                .soldeCaisse(caisseOpt.map(c -> nz(c.getSolde())).orElse(BigDecimal.ZERO))
                .soldeSolidarite(solOpt.map(c -> nz(c.getSolde())).orElse(BigDecimal.ZERO))
                .entreesCaisseMois(fluxCaisse[0])
                .sortiesCaisseMois(fluxCaisse[1])
                .entreesSolidariteMois(fluxSol[0])
                .sortiesSolidariteMois(fluxSol[1])
                .build();
    }

    private BigDecimal variationJourCaisseEtSolidarite(Long orgId, LocalDate today) {
        List<Long> ids = idsComptesCaisseEtSolidariteOrg(orgId);
        if (ids.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return nz(mouvementCompteRepository.sumVariationComptesPourDate(orgId, ids, today));
    }

    private List<Long> idsComptesCaisseEtSolidariteOrg(Long orgId) {
        List<Long> ids = new ArrayList<>();
        for (TypeCompte type : List.of(TypeCompte.CAISSE, TypeCompte.SOLIDARITE)) {
            compteRepository
                    .findByOrganisationIdAndTypeCompteAndProprietaire(
                            orgId, type, ProprietaireCompte.ORGANISATION)
                    .map(Compte::getId)
                    .ifPresent(ids::add);
        }
        return ids;
    }

    /** Agrège crédits / débits sur la période (même logique que le relevé, hors opérations annulées). */
    private BigDecimal[] entreesSortiesPeriode(Long orgId, Long compteId, LocalDate debut, LocalDate fin) {
        if (debut.isAfter(fin)) {
            return new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO};
        }
        List<MouvementCompte> mouvements =
                mouvementCompteRepository.findByOrganisationAndCompteBetween(orgId, compteId, debut, fin);
        return entreesSortiesDepuisMouvements(mouvements);
    }

    private BigDecimal[] entreesSortiesDepuisMouvements(List<MouvementCompte> mouvements) {
        BigDecimal entrees = BigDecimal.ZERO;
        BigDecimal sorties = BigDecimal.ZERO;
        for (MouvementCompte mc : mouvements) {
            Operation op = mc.getOperation();
            if (op == null || Boolean.TRUE.equals(op.getAnnulee()) || op.getOperationOrigineId() != null) {
                continue;
            }
            if (mc.getSens() == SensMouvement.CREDIT) {
                entrees = entrees.add(nz(mc.getMontant()));
            } else {
                sorties = sorties.add(nz(mc.getMontant()));
            }
        }
        return new BigDecimal[] {entrees, sorties};
    }

    private List<ReleveLigneResponse> construireLignes(
            List<MouvementCompte> mouvements,
            BigDecimal soldeFinPeriode,
            String typeFiltre,
            String statutFiltre,
            String recherche,
            Map<Long, Membre> membres,
            boolean releveMembre) {

        BigDecimal running = nz(soldeFinPeriode);
        List<ReleveLigneResponse> lignes = new ArrayList<>();

        for (MouvementCompte mc : mouvements) {
            ReleveLigneResponse ligne = toLigne(mc, running, membres, releveMembre);
            if (!passeFiltre(ligne, typeFiltre, statutFiltre, recherche)) {
                running = reculerSolde(running, mc, releveMembre);
                continue;
            }
            lignes.add(ligne);
            running = reculerSolde(running, mc, releveMembre);
        }
        return lignes;
    }

    /**
     * Anciens octrois : capital membre enregistré en CREDIT ; affichage relevé membre en débit.
     */
    private SensMouvement sensEffectifReleve(MouvementCompte mc, boolean releveMembre) {
        if (!releveMembre || mc.getSens() != SensMouvement.CREDIT) {
            return mc.getSens();
        }
        Operation op = mc.getOperation();
        if (op == null || op.getTypeOperation() != TypeOperation.EMPRUNT) {
            return mc.getSens();
        }
        BigDecimal capital = nz(op.getMontant());
        if (capital.compareTo(BigDecimal.ZERO) > 0 && nz(mc.getMontant()).compareTo(capital) == 0) {
            return SensMouvement.DEBIT;
        }
        return mc.getSens();
    }

    private BigDecimal reculerSolde(BigDecimal running, MouvementCompte mc, boolean releveMembre) {
        SensMouvement sens = sensEffectifReleve(mc, releveMembre);
        if (sens == SensMouvement.CREDIT) {
            return running.subtract(mc.getMontant());
        }
        return running.add(mc.getMontant());
    }

    private ReleveLigneResponse toLigne(
            MouvementCompte mc, BigDecimal soldeApres, Map<Long, Membre> membres, boolean releveMembre) {
        Operation op = mc.getOperation();
        SensMouvement sens = sensEffectifReleve(mc, releveMembre);
        String sensLibelle = sens == SensMouvement.CREDIT ? "credit" : "debit";
        TypeMeta meta = typeMeta(op.getTypeOperation());
        String membreNom = null;
        String codeMembre = null;
        if (op.getMembreId() != null) {
            Membre m = membres.get(op.getMembreId());
            if (m != null) {
                membreNom = m.getNomComplet();
                codeMembre = m.getCodeMembre();
            }
        }
        String titre = buildTitre(op, membreNom, codeMembre);
        return ReleveLigneResponse.builder()
                .operationId(op.getId())
                .dateOperation(op.getDateOperation())
                .heureOperation(op.getDateCreation() != null ? op.getDateCreation().toLocalTime() : null)
                .titre(titre)
                .typeOperation(op.getTypeOperation().name())
                .typeLibelle(meta.libelle)
                .typeTagClass(meta.tagClass)
                .sens(sensLibelle)
                .montant(mc.getMontant())
                .soldeApres(soldeApres)
                .annulee(Boolean.TRUE.equals(op.getAnnulee()))
                .contrepassation(op.getOperationOrigineId() != null)
                .reference("OP-" + op.getId())
                .membreNom(membreNom)
                .codeMembre(codeMembre)
                .icone(meta.icone)
                .iconeBg(meta.iconeBg)
                .metaExtra(op.getObservation())
                .build();
    }

    private boolean passeFiltre(ReleveLigneResponse l, String typeFiltre, String statutFiltre, String recherche) {
        if (typeFiltre != null && !typeFiltre.isBlank()) {
            String tf = typeFiltre.toLowerCase(Locale.ROOT);
            if (!correspondTypeFiltre(l.getTypeOperation(), tf)) {
                return false;
            }
        }
        if (statutFiltre != null && !statutFiltre.isBlank()) {
            if ("active".equalsIgnoreCase(statutFiltre) && l.isAnnulee()) {
                return false;
            }
            if ("annulee".equalsIgnoreCase(statutFiltre) && !l.isAnnulee()) {
                return false;
            }
        }
        if (recherche != null && !recherche.isBlank()) {
            String q = recherche.toLowerCase(Locale.ROOT);
            String blob = (l.getTitre() + " " + nzStr(l.getMembreNom()) + " " + nzStr(l.getCodeMembre())
                            + " " + nzStr(l.getReference()) + " " + nzStr(l.getMetaExtra()))
                    .toLowerCase(Locale.ROOT);
            if (!blob.contains(q)) {
                return false;
            }
        }
        return true;
    }

    private boolean correspondTypeFiltre(String typeOp, String filtre) {
        return switch (filtre) {
            case "cotisation" -> "COTISATION".equals(typeOp);
            case "mois" -> "COTISATION_MOIS".equals(typeOp);
            case "versement" -> "VERSEMENT".equals(typeOp) || typeOp.startsWith("BANQUE");
            case "emprunt" -> "EMPRUNT".equals(typeOp);
            case "remboursement" -> "REMBOURSEMENT".equals(typeOp);
            case "penalite" -> "PENALITE".equals(typeOp);
            case "amende" -> "AMENDE".equals(typeOp);
            case "depense" -> "DEPENSE".equals(typeOp);
            case "banque" -> typeOp.startsWith("BANQUE");
            default -> true;
        };
    }

    private List<ReleveGroupeResponse> grouperParDate(List<ReleveLigneResponse> lignes) {
        LinkedHashMap<LocalDate, List<ReleveLigneResponse>> map = new LinkedHashMap<>();
        for (ReleveLigneResponse l : lignes) {
            map.computeIfAbsent(l.getDateOperation(), d -> new ArrayList<>()).add(l);
        }
        List<ReleveGroupeResponse> groupes = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ReleveLigneResponse>> e : map.entrySet()) {
            String label = "📅 " + capitalize(GROUPE_DATE.format(e.getKey()));
            groupes.add(ReleveGroupeResponse.builder()
                    .date(e.getKey())
                    .label(label)
                    .lignes(e.getValue())
                    .build());
        }
        return groupes;
    }

    private ReleveTotauxResponse calculerTotaux(List<ReleveLigneResponse> lignes) {
        BigDecimal entrees = BigDecimal.ZERO;
        BigDecimal sorties = BigDecimal.ZERO;
        int annulees = 0;
        for (ReleveLigneResponse l : lignes) {
            if (l.isAnnulee() || l.isContrepassation()) {
                annulees++;
                continue;
            }
            if ("credit".equals(l.getSens())) {
                entrees = entrees.add(l.getMontant());
            } else {
                sorties = sorties.add(l.getMontant());
            }
        }
        return ReleveTotauxResponse.builder()
                .entrees(entrees)
                .sorties(sorties)
                .variationNette(entrees.subtract(sorties))
                .nbOperations(lignes.size())
                .nbAnnulees(annulees)
                .build();
    }

    private Compte resolveCompteOrg(Long orgId, TypeCompte type, Long compteId) {
        if (compteId != null) {
            return compteRepository.findById(compteId)
                    .filter(c -> Objects.equals(c.getOrganisationId(), orgId))
                    .filter(c -> c.getProprietaire() == ProprietaireCompte.ORGANISATION)
                    .orElseThrow(() -> new BusinessException("Compte introuvable"));
        }
        return compteService.getCompteOrg(orgId, type);
    }

    private TypeCompte typeCompteFromScope(String scope, Long compteId) {
        if (compteId != null) {
            return null;
        }
        if (scope == null || scope.isBlank() || "caisse".equalsIgnoreCase(scope)) {
            return TypeCompte.CAISSE;
        }
        if ("banque".equalsIgnoreCase(scope)) {
            return TypeCompte.BANQUE;
        }
        if ("sol".equalsIgnoreCase(scope) || "solidarite".equalsIgnoreCase(scope)) {
            return TypeCompte.SOLIDARITE;
        }
        if ("interet".equalsIgnoreCase(scope) || "interets".equalsIgnoreCase(scope)) {
            return TypeCompte.INTERET;
        }
        if ("amendes".equalsIgnoreCase(scope)) {
            return TypeCompte.AMENDES;
        }
        throw new BusinessException("Type de compte inconnu : " + scope);
    }

    private CompteMembreResumeResponse toMembreResume(MembreSoldesResponse s, Membre m) {
        BigDecimal epargne = nz(s.getEpargneHebdo()).add(nz(s.getEpargneMois()));
        BigDecimal depense = nz(s.getDepense());
        BigDecimal total = epargne.add(nz(s.getSolidarite())).subtract(depense).add(nz(s.getPenalite())).add(nz(s.getAmende()));
        String nom = m != null ? m.getNomComplet() : "Membre #" + s.getMembreId();
        String code = m != null ? m.getCodeMembre() : "";
        return CompteMembreResumeResponse.builder()
                .membreId(s.getMembreId())
                .nomComplet(nom)
                .codeMembre(code)
                .posteLabel(m != null ? posteLabel(m.getPoste()) : "")
                .initials(initials(nom))
                .avatarColor(couleurAvatar(code))
                .totalSoldes(total)
                .epargne(epargne)
                .solidarite(nz(s.getSolidarite()))
                .depense(depense)
                .penalite(nz(s.getPenalite()))
                .amende(nz(s.getAmende()))
                .build();
    }

    private String buildTitre(Operation op, String membreNom, String codeMembre) {
        String type = typeMeta(op.getTypeOperation()).libelle;
        if (membreNom != null && codeMembre != null) {
            return type + " — " + membreNom + " (" + codeMembre + ")";
        }
        if (op.getObservation() != null && !op.getObservation().isBlank()) {
            return type + " — " + op.getObservation();
        }
        return type;
    }

    private static TypeMeta typeMeta(TypeOperation type) {
        if (type == null) {
            return new TypeMeta("Opération", "cotisation", "💰", "var(--g3)");
        }
        return switch (type) {
            case COTISATION -> new TypeMeta("Cotisation hebdo", "cotisation", "💰", "var(--g3)");
            case COTISATION_MOIS -> new TypeMeta("Cotisation mensuelle", "mois", "📅", "var(--pi2)");
            case VERSEMENT -> new TypeMeta("Versement", "versement", "📥", "var(--bl2)");
            case REMBOURSEMENT -> new TypeMeta("Remboursement", "remboursement", "🔄", "var(--bl2)");
            case EMPRUNT -> new TypeMeta("Emprunt", "emprunt", "📋", "var(--re2)");
            case PENALITE -> new TypeMeta("Pénalité", "penalite", "⚠", "var(--re2)");
            case AMENDE -> new TypeMeta("Amende", "amende", "🚫", "var(--re2)");
            case DEPENSE -> new TypeMeta("Dépense", "depense", "📤", "var(--re2)");
            case BANQUE_VERSEMENT -> new TypeMeta("Versement banque", "banque", "🏦", "var(--bl2)");
            case BANQUE_RETRAIT -> new TypeMeta("Retrait banque", "banque", "🏦", "var(--bl2)");
            case REPARTITION_EXERCICE -> new TypeMeta("Répartition clôture", "repartition", "📊", "var(--g3)");
        };
    }

    private record TypeMeta(String libelle, String tagClass, String icone, String iconeBg) {}

    private static String libelleOrg(TypeCompte type, Compte c) {
        if (c.getLibelle() != null && !c.getLibelle().isBlank()) {
            return c.getLibelle();
        }
        return switch (type) {
            case CAISSE -> "Caisse principale";
            case BANQUE -> "Compte Banque";
            case SOLIDARITE -> "Fonds Solidarité";
            case INTERET -> "Compte intérêts";
            case AMENDES -> "Compte amendes & pénalités";
            default -> type.name();
        };
    }

    private static String sousTitreOrg(TypeCompte type) {
        return switch (type) {
            case CAISSE -> "Compte organisation";
            case BANQUE -> "Compte bancaire";
            case SOLIDARITE -> "Compte organisation";
            case INTERET -> "Frais & intérêts emprunts";
            case AMENDES -> "Encaissements organisation";
            default -> "";
        };
    }

    private static String iconeOrg(TypeCompte type) {
        return switch (type) {
            case CAISSE -> "💵";
            case BANQUE -> "🏛";
            case SOLIDARITE -> "🤝";
            case INTERET -> "📈";
            case AMENDES -> "⚖";
            default -> "🏦";
        };
    }

    private static String couleurFondOrg(TypeCompte type) {
        return switch (type) {
            case CAISSE -> "var(--g3)";
            case BANQUE -> "var(--bl2)";
            case SOLIDARITE -> "var(--or3)";
            case INTERET -> "var(--pu2)";
            case AMENDES -> "var(--re2)";
            default -> "var(--g3)";
        };
    }

    private static String posteLabel(PosteMembre poste) {
        if (poste == null) {
            return "Membre";
        }
        return switch (poste) {
            case PRESIDENT -> "Président(e)";
            case VICE_PRESIDENT -> "Vice-président(e)";
            case SECRETAIRE_GENERAL -> "Secrétaire général";
            case SECRETAIRE_GENERAL_ADJOINT -> "S.G.A.";
            case TRESORIER -> "Trésorier(ère)";
            case TRESORIER_ADJOINT -> "Trésorier(ère) adjoint";
            case COMMISSAIRE_AUX_COMPTES -> "Commissaire au compte";
            case SUPERVISEUR -> "Superviseur";
            default -> "Membre simple";
        };
    }

    private static String initials(String nom) {
        if (nom == null || nom.isBlank()) {
            return "?";
        }
        return Arrays.stream(nom.trim().split("\\s+"))
                .filter(s -> !s.isEmpty())
                .map(s -> String.valueOf(s.charAt(0)))
                .collect(Collectors.joining())
                .toUpperCase(Locale.ROOT)
                .substring(0, Math.min(2, nom.trim().split("\\s+").length));
    }

    private static String couleurAvatar(String code) {
        if (code == null || code.length() < 2) {
            return "#2d7a52";
        }
        int h = Math.abs(code.hashCode() % 360);
        return "hsl(" + h + ",45%,42%)";
    }

    private static String formatDateFr(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.FRENCH) + s.substring(1);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String nzStr(String s) {
        return s != null ? s : "";
    }
}
