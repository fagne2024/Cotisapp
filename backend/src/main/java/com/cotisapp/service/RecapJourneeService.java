package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.JourneeReunion;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.entity.Organisation;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.StatutPlanad;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.request.CreerJourneeReunionRequest;
import com.cotisapp.dto.response.*;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.JourneeReunionRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.MouvementCompteRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecapJourneeService {

    private static final Set<TypeOperation> TYPES_COTISATION = Set.of(
            TypeOperation.COTISATION,
            TypeOperation.COTISATION_MOIS,
            TypeOperation.VERSEMENT);
    private static final List<TypeCompte> COMPTES_ORG_RECAP = List.of(
            TypeCompte.CAISSE,
            TypeCompte.SOLIDARITE,
            TypeCompte.BANQUE);

    private final JourneeReunionRepository journeeReunionRepository;
    private final OrganisationRepository organisationRepository;
    private final ExerciceService exerciceService;
    private final OperationRepository operationRepository;
    private final CompteRepository compteRepository;
    private final MouvementCompteRepository mouvementCompteRepository;
    private final MembreRepository membreRepository;
    private final OperationPlanadGuardService operationPlanadGuardService;
    private final PlanadOuvertureService planadOuvertureService;

    @Transactional
    public void synchroniserJourneesDepuisOperations(Long orgId, Long exerciceId) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));
        if (journeeReunionRepository.findPlanadOuvert(exerciceId).isPresent()) {
            return;
        }
        List<LocalDate> dates = journeeReunionRepository.findDistinctDatesOperations(orgId, exerciceId);
        Organisation org = organisationRepository.findById(orgId).orElseThrow();
        Map<LocalDate, JourneeReunion> existantes = journeeReunionRepository
                .findByExerciceIdOrderByDateReunionAsc(exerciceId)
                .stream()
                .collect(Collectors.toMap(JourneeReunion::getDateReunion, j -> j, (a, b) -> a));
        List<LocalDate> manquantes = dates.stream().filter(d -> !existantes.containsKey(d)).sorted().toList();
        if (manquantes.isEmpty()) {
            return;
        }
        int maxNumero = journeeReunionRepository.findMaxNumero(exerciceId);
        for (int i = 0; i < manquantes.size(); i++) {
            LocalDate date = manquantes.get(i);
            maxNumero++;
            boolean dernier = i == manquantes.size() - 1;
            journeeReunionRepository.save(JourneeReunion.builder()
                    .organisationId(orgId)
                    .exerciceId(exerciceId)
                    .numero(maxNumero)
                    .dateReunion(date)
                    .libelle(libelleJournee(org.getCode(), maxNumero))
                    .statut(dernier ? StatutPlanad.OUVERT : StatutPlanad.CLOTURE)
                    .dateCloture(dernier ? null : date)
                    .build());
        }
    }

    @Transactional
    public List<JourneeReunionResponse> lister(Long orgId, Long exerciceIdParam) {
        Long exerciceId = resoudreExerciceId(orgId, exerciceIdParam);
        synchroniserJourneesDepuisOperations(orgId, exerciceId);
        return journeeReunionRepository.findByExerciceIdOrderByNumeroDesc(exerciceId).stream()
                .map(j -> toJourneeResponse(j, orgId))
                .toList();
    }

    private Long resoudreExerciceId(Long orgId, Long exerciceIdParam) {
        if (exerciceIdParam != null) {
            exerciceService.verifierExerciceAppartientOrg(orgId, exerciceIdParam);
            return exerciceIdParam;
        }
        return exerciceService.requireExerciceCourantId(orgId);
    }

    @Transactional
    public JourneeReunionResponse creer(Long orgId, CreerJourneeReunionRequest request) {
        if (request.getDateReunion() == null) {
            throw new BusinessException("La date de réunion est obligatoire");
        }
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new BusinessException("Organisation introuvable"));
        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        if (journeeReunionRepository.findByExerciceIdAndDateReunion(exerciceId, request.getDateReunion()).isPresent()) {
            throw new BusinessException("Une journée existe déjà pour cette date : "
                    + request.getDateReunion());
        }
        operationPlanadGuardService.verifierPeutOuvrirNouveauPlanad(exerciceId);
        int numero = journeeReunionRepository.findMaxNumero(exerciceId) + 1;
        JourneeReunion j = journeeReunionRepository.save(JourneeReunion.builder()
                .organisationId(orgId)
                .exerciceId(exerciceId)
                .numero(numero)
                .dateReunion(request.getDateReunion())
                .libelle(libelleJournee(org.getCode(), numero))
                .build());
        return toJourneeResponse(j, orgId);
    }

    @Transactional
    public JourneeReunionResponse cloturer(Long orgId, Long journeeId) {
        JourneeReunion j = journeeReunionRepository.findByIdAndOrganisationId(journeeId, orgId)
                .orElseThrow(() -> new BusinessException("Journée de réunion introuvable"));
        exerciceService.verifierExerciceCourant(orgId, j.getExerciceId());
        if (j.getStatut() == StatutPlanad.CLOTURE) {
            throw new BusinessException("Ce PLANAD est déjà clôturé");
        }
        j.setStatut(StatutPlanad.CLOTURE);
        j.setDateCloture(LocalDate.now());
        journeeReunionRepository.save(j);
        planadOuvertureService.ouvrirPlanadSuivantApresCloture(orgId, j.getExerciceId(), j.getDateReunion());
        return toJourneeResponse(j, orgId);
    }

    /**
     * Réouverture d'un PLANAD clôturé — réservée au superadmin (contrôle au niveau controller).
     */
    @Transactional
    public JourneeReunionResponse reouvrir(Long orgId, Long journeeId) {
        JourneeReunion j = journeeReunionRepository.findByIdAndOrganisationId(journeeId, orgId)
                .orElseThrow(() -> new BusinessException("Journée de réunion introuvable"));
        exerciceService.verifierExerciceAppartientOrg(orgId, j.getExerciceId());
        if (j.getStatut() != StatutPlanad.CLOTURE) {
            throw new BusinessException("Ce PLANAD est déjà ouvert");
        }
        if (journeeReunionRepository.existsByExerciceIdAndStatut(j.getExerciceId(), StatutPlanad.OUVERT)) {
            String libelle = journeeReunionRepository.findPlanadOuvert(j.getExerciceId())
                    .map(JourneeReunion::getLibelle)
                    .orElse("PLANAD ouvert");
            throw new BusinessException(
                    "Impossible de réouvrir : clôturez d'abord le " + libelle + " de cet exercice");
        }
        j.setStatut(StatutPlanad.OUVERT);
        j.setDateCloture(null);
        journeeReunionRepository.save(j);
        return toJourneeResponse(j, orgId);
    }

    @Transactional(readOnly = true)
    public RecapJourneeResponse recapParId(Long orgId, Long journeeId) {
        JourneeReunion j = journeeReunionRepository.findByIdAndOrganisationId(journeeId, orgId)
                .orElseThrow(() -> new BusinessException("Journée de réunion introuvable"));
        return construireRecap(orgId, j);
    }

    @Transactional(readOnly = true)
    public List<JourneeReunionResponse> listerJourneesPourMembre(Long orgId, Long membreId) {
        verifierMembre(orgId, membreId);
        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        synchroniserJourneesDepuisOperations(orgId, exerciceId);
        return journeeReunionRepository.findByExerciceIdOrderByNumeroDesc(exerciceId).stream()
                .map(j -> toJourneeResponsePourMembre(j, orgId, membreId))
                .filter(j -> j.getNbOperations() > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecapMembreJourneeResponse recapPourMembre(Long orgId, Long journeeId, Long membreId) {
        verifierMembre(orgId, membreId);
        JourneeReunion j = journeeReunionRepository.findByIdAndOrganisationId(journeeId, orgId)
                .orElseThrow(() -> new BusinessException("Journée de réunion introuvable"));
        return construireRecapMembre(orgId, j, membreId);
    }

    @Transactional
    public RecapMembreJourneeResponse recapPourMembreParDate(Long orgId, LocalDate date, Long membreId) {
        verifierMembre(orgId, membreId);
        if (date == null) {
            throw new BusinessException("Date requise");
        }
        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        synchroniserJourneesDepuisOperations(orgId, exerciceId);
        JourneeReunion j = journeeReunionRepository.findByExerciceIdAndDateReunion(exerciceId, date)
                .orElseGet(() -> creerJourneePourDate(orgId, exerciceId, date));
        return construireRecapMembre(orgId, j, membreId);
    }

    @Transactional
    public RecapJourneeResponse recapParDate(Long orgId, LocalDate date) {
        if (date == null) {
            throw new BusinessException("Date requise");
        }
        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        synchroniserJourneesDepuisOperations(orgId, exerciceId);
        JourneeReunion j = journeeReunionRepository.findByExerciceIdAndDateReunion(exerciceId, date)
                .orElseGet(() -> creerJourneePourDate(orgId, exerciceId, date));
        return construireRecap(orgId, j);
    }

    private JourneeReunion creerJourneePourDate(Long orgId, Long exerciceId, LocalDate date) {
        operationPlanadGuardService.verifierPeutOuvrirNouveauPlanad(exerciceId);
        return planadOuvertureService.creerPlanadOuvert(orgId, exerciceId, date);
    }

    private void verifierMembre(Long orgId, Long membreId) {
        membreRepository
                .findByIdAndOrganisationId(membreId, orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));
    }

    private RecapMembreJourneeResponse construireRecapMembre(Long orgId, JourneeReunion journee, Long membreId) {
        LocalDate date = journee.getDateReunion();
        List<Operation> operationsJour =
                operationRepository.findByOrganisationIdAndExerciceIdAndDateOperationWithMouvements(
                        orgId, journee.getExerciceId(), date).stream()
                        .filter(op -> membreId.equals(op.getMembreId()))
                        .toList();

        Map<Long, Membre> membresCache = new HashMap<>();
        Membre membre = membresCache.computeIfAbsent(
                membreId, id -> membreRepository.findByIdAndOrganisationId(id, orgId).orElse(null));

        List<RecapOperationLigneResponse> lignes = new ArrayList<>();
        RecapJourneeSyntheseResponse synthese = RecapJourneeSyntheseResponse.builder()
                .montantCotisations(BigDecimal.ZERO)
                .montantEmprunts(BigDecimal.ZERO)
                .montantRemboursements(BigDecimal.ZERO)
                .entreesCaisse(BigDecimal.ZERO)
                .sortiesCaisse(BigDecimal.ZERO)
                .build();
        int nbAnnulations = 0;

        for (Operation op : operationsJour) {
            boolean annulation = op.getOperationOrigineId() != null;
            boolean annulee = Boolean.TRUE.equals(op.getAnnulee());
            if (annulation) {
                nbAnnulations++;
            }
            BigDecimal frais = op.getMontantFrais() != null ? op.getMontantFrais() : BigDecimal.ZERO;
            BigDecimal montant = op.getMontant() != null ? op.getMontant() : BigDecimal.ZERO;
            BigDecimal total = montant.add(frais);

            lignes.add(RecapOperationLigneResponse.builder()
                    .operationId(op.getId())
                    .typeOperation(op.getTypeOperation())
                    .typeLibelle(libelleType(op.getTypeOperation()))
                    .membreId(op.getMembreId())
                    .membreNom(membre != null ? membre.getNomComplet() : null)
                    .codeMembre(membre != null ? membre.getCodeMembre() : null)
                    .montant(montant)
                    .montantFrais(frais)
                    .montantTotal(total)
                    .dateOperation(op.getDateOperation())
                    .observation(op.getObservation())
                    .annulee(annulee)
                    .annulation(annulation)
                    .build());

            if (!annulee && !annulation) {
                accumulerSynthese(
                        synthese,
                        op.getTypeOperation(),
                        montantPourRecapSynthese(op.getTypeOperation(), montant, frais));
            }
        }

        synthese.setNbOperationsActives((int) lignes.stream()
                .filter(l -> !l.isAnnulee() && !l.isAnnulation())
                .count());
        synthese.setNbAnnulations(nbAnnulations);
        synthese.setNbMembresConcernes(operationsJour.isEmpty() ? 0 : 1);

        List<Operation> toutesOpsJour =
                operationRepository.findByOrganisationIdAndExerciceIdAndDateOperationWithMouvements(
                        orgId, journee.getExerciceId(), date);
        RecapMembreResponse resume = construireMembres(orgId, toutesOpsJour, membresCache).stream()
                .filter(m -> membreId.equals(m.getMembreId()))
                .findFirst()
                .orElse(RecapMembreResponse.builder()
                        .membreId(membreId)
                        .codeMembre(membre != null ? membre.getCodeMembre() : null)
                        .membreNom(membre != null ? membre.getNomComplet() : null)
                        .nbOperations(0)
                        .montantCotisations(BigDecimal.ZERO)
                        .montantEmprunts(BigDecimal.ZERO)
                        .montantRemboursements(BigDecimal.ZERO)
                        .variationNetComptes(BigDecimal.ZERO)
                        .build());

        return RecapMembreJourneeResponse.builder()
                .journeeId(journee.getId())
                .libelle(journee.getLibelle())
                .numero(journee.getNumero())
                .dateReunion(date)
                .resume(resume)
                .synthese(synthese)
                .operations(lignes)
                .build();
    }

    private JourneeReunionResponse toJourneeResponsePourMembre(JourneeReunion j, Long orgId, Long membreId) {
        List<Operation> ops = operationRepository
                .findByOrganisationIdAndExerciceIdAndDateOperationWithMouvements(
                        orgId, j.getExerciceId(), j.getDateReunion())
                .stream()
                .filter(o -> membreId.equals(o.getMembreId()))
                .toList();
        int nbCotisations = 0;
        int nbEmprunts = 0;
        int nbRemboursements = 0;
        int nb = 0;
        for (Operation o : ops) {
            if (Boolean.TRUE.equals(o.getAnnulee()) || o.getOperationOrigineId() != null) {
                continue;
            }
            nb++;
            if (TYPES_COTISATION.contains(o.getTypeOperation())) {
                nbCotisations++;
            } else if (o.getTypeOperation() == TypeOperation.EMPRUNT) {
                nbEmprunts++;
            } else if (o.getTypeOperation() == TypeOperation.REMBOURSEMENT) {
                nbRemboursements++;
            }
        }
        return journeeResponse(j, nb, nbCotisations, nbEmprunts, nbRemboursements);
    }

    private RecapJourneeResponse construireRecap(Long orgId, JourneeReunion journee) {
        Organisation org = organisationRepository.findById(orgId).orElseThrow();
        LocalDate date = journee.getDateReunion();
        List<Operation> operations = operationRepository.findByOrganisationIdAndExerciceIdAndDateOperationWithMouvements(
                orgId, journee.getExerciceId(), date);

        Map<Long, Membre> membresCache = new HashMap<>();
        List<RecapOperationLigneResponse> lignes = new ArrayList<>();
        RecapJourneeSyntheseResponse synthese = RecapJourneeSyntheseResponse.builder()
                .montantCotisations(BigDecimal.ZERO)
                .montantEmprunts(BigDecimal.ZERO)
                .montantRemboursements(BigDecimal.ZERO)
                .entreesCaisse(BigDecimal.ZERO)
                .sortiesCaisse(BigDecimal.ZERO)
                .build();

        Set<Long> membresIds = new HashSet<>();
        int nbAnnulations = 0;

        for (Operation op : operations) {
            boolean annulation = op.getOperationOrigineId() != null;
            boolean annulee = Boolean.TRUE.equals(op.getAnnulee());
            if (annulation) {
                nbAnnulations++;
            }
            Membre membre = null;
            if (op.getMembreId() != null) {
                membresIds.add(op.getMembreId());
                membre = membresCache.computeIfAbsent(op.getMembreId(),
                        id -> membreRepository.findById(id).orElse(null));
            }
            BigDecimal frais = op.getMontantFrais() != null ? op.getMontantFrais() : BigDecimal.ZERO;
            BigDecimal montant = op.getMontant() != null ? op.getMontant() : BigDecimal.ZERO;
            BigDecimal total = montant.add(frais);

            lignes.add(RecapOperationLigneResponse.builder()
                    .operationId(op.getId())
                    .typeOperation(op.getTypeOperation())
                    .typeLibelle(libelleType(op.getTypeOperation()))
                    .membreId(op.getMembreId())
                    .membreNom(membre != null ? membre.getNomComplet() : null)
                    .codeMembre(membre != null ? membre.getCodeMembre() : null)
                    .montant(montant)
                    .montantFrais(frais)
                    .montantTotal(total)
                    .dateOperation(op.getDateOperation())
                    .observation(op.getObservation())
                    .annulee(annulee)
                    .annulation(annulation)
                    .build());

            if (!annulee && !annulation) {
                accumulerSynthese(
                        synthese,
                        op.getTypeOperation(),
                        montantPourRecapSynthese(op.getTypeOperation(), montant, frais));
            }
        }

        synthese.setNbOperationsActives((int) lignes.stream()
                .filter(l -> !l.isAnnulee() && !l.isAnnulation())
                .count());
        synthese.setNbAnnulations(nbAnnulations);
        synthese.setNbMembresConcernes(membresIds.size());

        List<RecapCompteResponse> comptes = construireComptesOrg(orgId, date);
        comptes.stream()
                .filter(c -> c.getTypeCompte() == TypeCompte.CAISSE)
                .findFirst()
                .ifPresent(c -> {
                    BigDecimal v = c.getVariationJour();
                    if (v.compareTo(BigDecimal.ZERO) >= 0) {
                        synthese.setEntreesCaisse(v);
                        synthese.setSortiesCaisse(BigDecimal.ZERO);
                    } else {
                        synthese.setEntreesCaisse(BigDecimal.ZERO);
                        synthese.setSortiesCaisse(v.abs());
                    }
                });
        List<RecapMembreResponse> membres = construireMembres(orgId, operations, membresCache);

        return RecapJourneeResponse.builder()
                .journeeId(journee.getId())
                .codeOrganisation(org.getCode())
                .libelle(journee.getLibelle())
                .numero(journee.getNumero())
                .dateReunion(date)
                .synthese(synthese)
                .comptesOrganisation(comptes)
                .membres(membres)
                .operations(lignes)
                .build();
    }

    /** Emprunt accordé = capital uniquement ; cotisations / remboursements incluent les frais le cas échéant. */
    private static BigDecimal montantPourRecapSynthese(TypeOperation type, BigDecimal montant, BigDecimal frais) {
        if (type == TypeOperation.EMPRUNT) {
            return montant;
        }
        return montant.add(frais);
    }

    private void accumulerSynthese(RecapJourneeSyntheseResponse s, TypeOperation type, BigDecimal total) {
        if (TYPES_COTISATION.contains(type)) {
            s.setNbCotisations(s.getNbCotisations() + 1);
            s.setMontantCotisations(s.getMontantCotisations().add(total));
        } else if (type == TypeOperation.EMPRUNT) {
            s.setNbEmprunts(s.getNbEmprunts() + 1);
            s.setMontantEmprunts(s.getMontantEmprunts().add(total));
        } else if (type == TypeOperation.REMBOURSEMENT) {
            s.setNbRemboursements(s.getNbRemboursements() + 1);
            s.setMontantRemboursements(s.getMontantRemboursements().add(total));
        }
    }

    private List<RecapCompteResponse> construireComptesOrg(Long orgId, LocalDate date) {
        List<RecapCompteResponse> result = new ArrayList<>();
        for (TypeCompte type : COMPTES_ORG_RECAP) {
            Optional<Compte> compteOpt = compteRepository.findByOrganisationIdAndTypeCompteAndProprietaire(
                    orgId, type, ProprietaireCompte.ORGANISATION);
            if (compteOpt.isEmpty()) {
                continue;
            }
            Compte compte = compteOpt.get();
            BigDecimal variation = mouvementCompteRepository.sumVariationComptePourDate(orgId, compte.getId(), date);
            if (variation == null) {
                variation = BigDecimal.ZERO;
            }
            BigDecimal apres = mouvementCompteRepository.sumVariationCompteApresDate(orgId, compte.getId(), date);
            if (apres == null) {
                apres = BigDecimal.ZERO;
            }
            BigDecimal soldeActuel = compte.getSolde() != null ? compte.getSolde() : BigDecimal.ZERO;
            BigDecimal soldeFin = soldeActuel.subtract(apres);

            result.add(RecapCompteResponse.builder()
                    .typeCompte(type)
                    .libelle(compte.getLibelle() != null ? compte.getLibelle() : type.name())
                    .variationJour(variation)
                    .soldeFinJournee(soldeFin)
                    .soldeActuel(soldeActuel)
                    .build());
        }
        return result;
    }

    private List<RecapMembreResponse> construireMembres(
            Long orgId,
            List<Operation> operations,
            Map<Long, Membre> cache) {
        Map<Long, AccMembreRecap> acc = new LinkedHashMap<>();

        for (Operation op : operations) {
            if (op.getMembreId() == null || Boolean.TRUE.equals(op.getAnnulee()) || op.getOperationOrigineId() != null) {
                continue;
            }
            Long mid = op.getMembreId();
            Membre m = cache.computeIfAbsent(mid, id -> membreRepository.findById(id).orElse(null));
            if (m == null) {
                continue;
            }
            AccMembreRecap a = acc.computeIfAbsent(mid, k -> new AccMembreRecap(m));
            BigDecimal frais = op.getMontantFrais() != null ? op.getMontantFrais() : BigDecimal.ZERO;
            BigDecimal montant = op.getMontant() != null ? op.getMontant() : BigDecimal.ZERO;
            BigDecimal total = montant.add(frais);
            a.nbOperations++;
            if (TYPES_COTISATION.contains(op.getTypeOperation())) {
                a.montantCotisations = a.montantCotisations.add(total);
            } else if (op.getTypeOperation() == TypeOperation.EMPRUNT) {
                a.montantEmprunts = a.montantEmprunts.add(montant);
            } else if (op.getTypeOperation() == TypeOperation.REMBOURSEMENT) {
                a.montantRemboursements = a.montantRemboursements.add(total);
            }
        }

        Map<Long, Long> compteToMembre = compteRepository.findByOrganisationId(orgId).stream()
                .filter(c -> c.getProprietaire() == ProprietaireCompte.MEMBRE && c.getMembreId() != null)
                .collect(Collectors.toMap(Compte::getId, Compte::getMembreId, (x, y) -> x));

        for (Operation op : operations) {
            if (Boolean.TRUE.equals(op.getAnnulee()) || op.getOperationOrigineId() != null) {
                continue;
            }
            for (var mv : op.getMouvements()) {
                Long membreId = compteToMembre.get(mv.getCompteId());
                if (membreId == null) {
                    continue;
                }
                AccMembreRecap a = acc.computeIfAbsent(membreId, k -> {
                    Membre m = cache.computeIfAbsent(k, id -> membreRepository.findById(id).orElse(null));
                    return m != null ? new AccMembreRecap(m) : null;
                });
                if (a == null) {
                    continue;
                }
                BigDecimal delta = mv.getSens() == com.cotisapp.domain.enums.SensMouvement.CREDIT
                        ? mv.getMontant() : mv.getMontant().negate();
                a.variationNet = a.variationNet.add(delta);
            }
        }

        return acc.values().stream()
                .map(AccMembreRecap::toResponse)
                .sorted(Comparator.comparing(RecapMembreResponse::getMembreNom, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static final class AccMembreRecap {
        final Long membreId;
        final String codeMembre;
        final String membreNom;
        int nbOperations;
        BigDecimal montantCotisations = BigDecimal.ZERO;
        BigDecimal montantEmprunts = BigDecimal.ZERO;
        BigDecimal montantRemboursements = BigDecimal.ZERO;
        BigDecimal variationNet = BigDecimal.ZERO;

        AccMembreRecap(Membre m) {
            this.membreId = m.getId();
            this.codeMembre = m.getCodeMembre();
            this.membreNom = m.getNomComplet();
        }

        RecapMembreResponse toResponse() {
            return RecapMembreResponse.builder()
                    .membreId(membreId)
                    .codeMembre(codeMembre)
                    .membreNom(membreNom)
                    .nbOperations(nbOperations)
                    .montantCotisations(montantCotisations)
                    .montantEmprunts(montantEmprunts)
                    .montantRemboursements(montantRemboursements)
                    .variationNetComptes(variationNet)
                    .build();
        }
    }

    private JourneeReunionResponse toJourneeResponse(JourneeReunion j, Long orgId) {
        List<Operation> ops = operationRepository.findByOrganisationIdAndExerciceIdAndDateOperationWithMouvements(
                orgId, j.getExerciceId(), j.getDateReunion());
        int nbCotisations = 0;
        int nbEmprunts = 0;
        int nbRemboursements = 0;
        int nb = 0;
        for (Operation o : ops) {
            if (Boolean.TRUE.equals(o.getAnnulee()) || o.getOperationOrigineId() != null) {
                continue;
            }
            nb++;
            if (TYPES_COTISATION.contains(o.getTypeOperation())) {
                nbCotisations++;
            } else if (o.getTypeOperation() == TypeOperation.EMPRUNT) {
                nbEmprunts++;
            } else if (o.getTypeOperation() == TypeOperation.REMBOURSEMENT) {
                nbRemboursements++;
            }
        }
        return journeeResponse(j, nb, nbCotisations, nbEmprunts, nbRemboursements);
    }

    private static JourneeReunionResponse journeeResponse(
            JourneeReunion j, int nb, int nbCotisations, int nbEmprunts, int nbRemboursements) {
        return JourneeReunionResponse.builder()
                .id(j.getId())
                .numero(j.getNumero())
                .dateReunion(j.getDateReunion())
                .libelle(j.getLibelle())
                .statut(j.getStatut())
                .dateCloture(j.getDateCloture())
                .nbOperations(nb)
                .nbCotisations(nbCotisations)
                .nbEmprunts(nbEmprunts)
                .nbRemboursements(nbRemboursements)
                .build();
    }

    static String libelleJournee(String codeOrg, int numero) {
        String code = codeOrg != null && !codeOrg.isBlank() ? codeOrg.trim().toUpperCase() : "GIE";
        return code + " n°" + numero;
    }

    private static String libelleType(TypeOperation type) {
        return switch (type) {
            case COTISATION -> "Cotisation hebdo";
            case COTISATION_MOIS -> "Cotisation mois";
            case VERSEMENT -> "Versement";
            case EMPRUNT -> "Octroi emprunt";
            case REMBOURSEMENT -> "Remboursement";
            case PENALITE -> "Pénalité";
            case AMENDE -> "Amende";
            case DEPENSE -> "Dépense";
            case BANQUE_VERSEMENT -> "Versement banque";
            case BANQUE_RETRAIT -> "Retrait banque";
            case REPARTITION_EXERCICE -> "Répartition clôture";
        };
    }
}
