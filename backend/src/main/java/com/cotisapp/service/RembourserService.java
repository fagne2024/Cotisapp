package com.cotisapp.service;

import com.cotisapp.domain.entity.*;
import com.cotisapp.domain.enums.*;
import com.cotisapp.dto.request.RembourserRequest;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.EcheanceRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.RegleOperationRepository;
import com.cotisapp.security.OrganisationContext;
import com.cotisapp.domain.enums.ModePaiement;
import com.cotisapp.util.ModePaiementHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RembourserService {

    private final EmpruntRepository empruntRepository;
    private final EcheanceRepository echeanceRepository;
    private final OperationRepository operationRepository;
    private final RegleOperationRepository regleOperationRepository;
    private final CompteService compteService;
    private final JournalService journalService;
    private final ExerciceService exerciceService;
    private final OperationPlanadGuardService operationPlanadGuardService;
    private final OperationMemeJourControleService operationMemeJourControleService;

    @Transactional
    public Operation rembourser(Long orgId, Long empruntId, RembourserRequest request) {
        Emprunt emprunt = empruntRepository.findWithEcheancesByIdAndOrganisationId(empruntId, orgId)
                .orElseThrow(() -> new BusinessException("Emprunt introuvable"));
        LocalDate datePaiement = request.getDatePaiement() != null ? request.getDatePaiement() : LocalDate.now();
        operationMemeJourControleService.verifierRemboursement(
                orgId, emprunt.getMembreId(), emprunt.getTypeEmprunt(), datePaiement);
        exerciceService.verifierExerciceCourant(orgId, emprunt.getExerciceId());
        RegleOperation regle = EmpruntRegleHelper.trouverRegleEmprunt(
                regleOperationRepository, orgId, emprunt.getTypeEmprunt());
        BigDecimal penalite = resoudrePenalite(emprunt, request, regle);
        validerRemboursement(emprunt, request, penalite, regle);

        return switch (emprunt.getTypeEmprunt()) {
            case ETALE -> appliquerRembEtale(orgId, emprunt, request, penalite);
            case SOLIDARITE -> appliquerRembSolidarite(orgId, emprunt, request, penalite);
            case CAISSE -> appliquerRembCaisse(orgId, emprunt, request, penalite);
        };
    }

    private Operation appliquerRembEtale(Long orgId, Emprunt emprunt, RembourserRequest request, BigDecimal penalite) {
        BigDecimal montant = resolveMontant(request);
        Echeance echeance = resolveEcheance(emprunt, request.getEcheanceId());
        LocalDate datePaiement = request.getDatePaiement() != null ? request.getDatePaiement() : LocalDate.now();
        return executerRemboursement(orgId, emprunt, echeance, montant, BigDecimal.ZERO,
                TypeCompte.CAISSE, montant, datePaiement, penalite, request);
    }

    private Operation appliquerRembSolidarite(Long orgId, Emprunt emprunt, RembourserRequest request, BigDecimal penalite) {
        BigDecimal montant = resolveMontant(request);
        LocalDate datePaiement = request.getDatePaiement() != null ? request.getDatePaiement() : LocalDate.now();
        Echeance echeance = request.getEcheanceId() != null
                ? resolveEcheance(emprunt, request.getEcheanceId())
                : null;

        EmpruntAvanceCaisseHelper.RepartitionRemboursement rep =
                EmpruntAvanceCaisseHelper.repartirRemboursement(emprunt, montant);

        Operation operation = creerOperationBase(orgId, emprunt, echeance, montant, BigDecimal.ZERO, datePaiement, penalite, request);
        String splitObs = EmpruntAvanceCaisseHelper.observationRemboursementSplit(rep.partCaisse(), rep.partSolidarite());
        if (splitObs != null) {
            operation.setObservation(EmpruntAvanceCaisseHelper.fusionnerObservation(operation.getObservation(), splitObs));
        }

        List<MouvementCompte> mouvements = new ArrayList<>();
        Compte compteMembre = compteMembrePourRemboursement(emprunt);
        Compte caisseOrg = compteService.getCompteOrg(orgId, TypeCompte.CAISSE);
        Compte solidariteOrg = compteService.getCompteOrg(orgId, TypeCompte.SOLIDARITE);

        if (rep.partCaisse().compareTo(BigDecimal.ZERO) > 0) {
            mouvements.add(addMouvement(operation, compteMembre.getId(), SensMouvement.CREDIT, rep.partCaisse()));
            mouvements.add(addMouvement(operation, caisseOrg.getId(), SensMouvement.CREDIT, rep.partCaisse()));
            compteService.appliquerMouvement(compteMembre.getId(), SensMouvement.CREDIT, rep.partCaisse());
            compteService.appliquerMouvement(caisseOrg.getId(), SensMouvement.CREDIT, rep.partCaisse());
        }
        if (rep.partSolidarite().compareTo(BigDecimal.ZERO) > 0) {
            mouvements.add(addMouvement(operation, compteMembre.getId(), SensMouvement.CREDIT, rep.partSolidarite()));
            mouvements.add(addMouvement(operation, solidariteOrg.getId(), SensMouvement.CREDIT, rep.partSolidarite()));
            compteService.appliquerMouvement(compteMembre.getId(), SensMouvement.CREDIT, rep.partSolidarite());
            compteService.appliquerMouvement(solidariteOrg.getId(), SensMouvement.CREDIT, rep.partSolidarite());
        }

        appliquerMouvementsPenalite(orgId, emprunt, operation, mouvements, penalite);

        if (rep.partCaisse().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dejaRemb = emprunt.getMontantRembourseAvanceCaisse() != null
                    ? emprunt.getMontantRembourseAvanceCaisse() : BigDecimal.ZERO;
            emprunt.setMontantRembourseAvanceCaisse(dejaRemb.add(rep.partCaisse()));
        }

        operation.setMouvements(mouvements);
        finaliserEmprunt(emprunt, echeance, montant, datePaiement);
        Operation saved = operationRepository.save(operation);
        journalService.enregistrer(orgId, "REMBOURSEMENT_SOLIDARITE", "Opération " + saved.getId());
        return saved;
    }

    private Operation appliquerRembCaisse(Long orgId, Emprunt emprunt, RembourserRequest request, BigDecimal penalite) {
        BigDecimal capital = request.getMontantCapital() != null ? request.getMontantCapital() : resolveMontant(request);
        BigDecimal frais = request.getMontantFrais() != null ? request.getMontantFrais() : BigDecimal.ZERO;
        Echeance echeance = resolveEcheance(emprunt, request.getEcheanceId());

        LocalDate datePaiement = request.getDatePaiement() != null ? request.getDatePaiement() : LocalDate.now();
        Operation operation = creerOperationBase(orgId, emprunt, echeance, capital, frais, datePaiement, penalite, request);
        List<MouvementCompte> mouvements = new ArrayList<>();

        Compte compteMembre = compteMembrePourRemboursement(emprunt);
        Compte caisse = compteService.getCompteOrg(orgId, TypeCompte.CAISSE);

        mouvements.add(addMouvement(operation, compteMembre.getId(), SensMouvement.CREDIT, capital));
        mouvements.add(addMouvement(operation, caisse.getId(), SensMouvement.CREDIT, capital));
        compteService.appliquerMouvement(compteMembre.getId(), SensMouvement.CREDIT, capital);
        compteService.appliquerMouvement(caisse.getId(), SensMouvement.CREDIT, capital);

        appliquerRemboursementFrais(emprunt, orgId, operation, mouvements, frais);
        appliquerMouvementsPenalite(orgId, emprunt, operation, mouvements, penalite);

        operation.setMouvements(mouvements);
        boolean empruntSolde = finaliserEmprunt(emprunt, echeance, capital.add(frais), datePaiement);
        if (empruntSolde) {
            appliquerTransfertFraisInteretEmpruntSolde(orgId, emprunt, operation, mouvements);
        }
        Operation saved = operationRepository.save(operation);
        journalService.enregistrer(orgId, "REMBOURSEMENT_CAISSE", "Opération " + saved.getId());
        return saved;
    }

    private Operation executerRemboursement(
            Long orgId, Emprunt emprunt, Echeance echeance,
            BigDecimal montant, BigDecimal frais, TypeCompte compteOrgCible, BigDecimal montantOrg,
            LocalDate datePaiement, BigDecimal penalite, RembourserRequest request) {
        BigDecimal fraisInteret = frais != null ? frais.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        if (emprunt.getTypeEmprunt() == TypeEmprunt.ETALE
                && echeance != null
                && montantOrg.compareTo(BigDecimal.ZERO) > 0
                && fraisInteret.compareTo(BigDecimal.ZERO) == 0) {
            fraisInteret = EmpruntCalculHelper.fraisPortionPaiementEcheance(emprunt, echeance, montantOrg);
        }
        BigDecimal capitalLiquide = montantOrg.subtract(fraisInteret).max(BigDecimal.ZERO);

        Operation operation = creerOperationBase(orgId, emprunt, echeance, capitalLiquide, fraisInteret, datePaiement, penalite, request);
        List<MouvementCompte> mouvements = new ArrayList<>();

        Compte compteMembre = compteMembrePourRemboursement(emprunt);
        Compte orgCompte = compteService.getCompteOrg(orgId, compteOrgCible);

        mouvements.add(addMouvement(operation, compteMembre.getId(), SensMouvement.CREDIT, capitalLiquide));
        mouvements.add(addMouvement(operation, orgCompte.getId(), SensMouvement.CREDIT, capitalLiquide));
        compteService.appliquerMouvement(compteMembre.getId(), SensMouvement.CREDIT, capitalLiquide);
        compteService.appliquerMouvement(orgCompte.getId(), SensMouvement.CREDIT, capitalLiquide);
        appliquerRemboursementFrais(emprunt, orgId, operation, mouvements, fraisInteret);

        appliquerMouvementsPenalite(orgId, emprunt, operation, mouvements, penalite);

        operation.setMouvements(mouvements);
        boolean empruntSolde = finaliserEmprunt(emprunt, echeance, montantOrg, datePaiement);
        if (empruntSolde) {
            appliquerTransfertFraisInteretEmpruntSolde(orgId, emprunt, operation, mouvements);
        }
        Operation saved = operationRepository.save(operation);
        journalService.enregistrer(orgId, "REMBOURSEMENT", "Opération " + saved.getId());
        return saved;
    }

    private Operation creerOperationBase(
            Long orgId,
            Emprunt emprunt,
            Echeance echeance,
            BigDecimal montant,
            BigDecimal frais,
            LocalDate date,
            BigDecimal penalite,
            RembourserRequest request) {
        operationPlanadGuardService.verifierDateOperationAutorisee(orgId, emprunt.getExerciceId(), date, false);
        ModePaiement modePaiement = ModePaiementHelper.parser(request.getModePaiement());
        String obsPenalite = penalite != null && penalite.compareTo(BigDecimal.ZERO) > 0
                ? "Pénalité retard: " + penalite.stripTrailingZeros().toPlainString()
                : null;
        String obs = request.getObservation();
        if (obs != null && obs.isBlank()) {
            obs = null;
        }
        if (obsPenalite != null) {
            obs = obs == null ? obsPenalite : obs + " · " + obsPenalite;
        }
        obs = ModePaiementHelper.enrichirObservation(obs, modePaiement, request.getReferencePaiement());
        String ref = request.getReferencePaiement();
        return Operation.builder()
                .organisationId(orgId)
                .exerciceId(emprunt.getExerciceId())
                .membreId(emprunt.getMembreId())
                .typeOperation(TypeOperation.REMBOURSEMENT)
                .montant(montant)
                .montantFrais(frais)
                .dateOperation(date)
                .empruntId(emprunt.getId())
                .echeanceId(echeance != null ? echeance.getId() : null)
                .utilisateurId(OrganisationContext.getUserId())
                .observation(obs)
                .modePaiement(modePaiement)
                .referencePaiement(ref != null && !ref.isBlank() ? ref.trim() : null)
                .build();
    }

    private void appliquerMouvementsPenalite(
            Long orgId,
            Emprunt emprunt,
            Operation operation,
            List<MouvementCompte> mouvements,
            BigDecimal penalite) {
        if (penalite == null || penalite.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Compte compteMembre = compteMembrePourRemboursement(emprunt);
        Compte compteAmendesOrg = compteService.ensureCompteOrganisationAmendes(orgId);
        mouvements.add(addMouvement(operation, compteMembre.getId(), SensMouvement.CREDIT, penalite));
        mouvements.add(addMouvement(operation, compteAmendesOrg.getId(), SensMouvement.CREDIT, penalite));
        compteService.appliquerMouvement(compteMembre.getId(), SensMouvement.CREDIT, penalite);
        compteService.appliquerMouvement(compteAmendesOrg.getId(), SensMouvement.CREDIT, penalite);
    }

    private MouvementCompte addMouvement(Operation op, Long compteId, SensMouvement sens, BigDecimal montant) {
        MouvementCompte mc = MouvementCompte.builder()
                .operation(op).compteId(compteId).sens(sens).montant(montant).build();
        op.getMouvements().add(mc);
        return mc;
    }

    /** @return true si l'emprunt vient de passer au statut {@link StatutEmprunt#SOLDE} */
    private boolean finaliserEmprunt(Emprunt emprunt, Echeance echeance, BigDecimal montant, LocalDate datePaiement) {
        if (echeance != null) {
            echeance.setMontantPaye(echeance.getMontantPaye().add(montant));
            echeance.setStatut(calculerStatutEcheance(echeance));
            echeance.setDatePaiement(datePaiement);
            echeanceRepository.save(echeance);
        }
        boolean etaitEnCours = emprunt.getStatut() == StatutEmprunt.EN_COURS;
        emprunt.setMontantRembourse(emprunt.getMontantRembourse().add(montant));
        if (emprunt.getMontantRembourse().compareTo(emprunt.getMontantTotal()) >= 0) {
            emprunt.setStatut(StatutEmprunt.SOLDE);
        }
        empruntRepository.save(emprunt);
        return etaitEnCours && emprunt.getStatut() == StatutEmprunt.SOLDE;
    }

    /** Frais remboursés : crédit compte membre (annule le débit à l'octroi) + crédit caisse. */
    private void appliquerRemboursementFrais(
            Emprunt emprunt,
            Long orgId,
            Operation operation,
            List<MouvementCompte> mouvements,
            BigDecimal frais) {
        if (frais == null || frais.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Compte compteMembre = compteMembrePourRemboursement(emprunt);
        Compte caisseOrg = compteService.getCompteOrg(orgId, TypeCompte.CAISSE);
        mouvements.add(addMouvement(operation, compteMembre.getId(), SensMouvement.CREDIT, frais));
        mouvements.add(addMouvement(operation, caisseOrg.getId(), SensMouvement.CREDIT, frais));
        compteService.appliquerMouvement(compteMembre.getId(), SensMouvement.CREDIT, frais);
        compteService.appliquerMouvement(caisseOrg.getId(), SensMouvement.CREDIT, frais);
    }

    /** Transfert des frais d'emprunt vers le compte intérêts à la clôture (emprunt soldé). */
    private void appliquerTransfertFraisInteretEmpruntSolde(
            Long orgId,
            Emprunt emprunt,
            Operation operation,
            List<MouvementCompte> mouvements) {
        BigDecimal totalFrais = emprunt.getMontantFrais() != null ? emprunt.getMontantFrais() : BigDecimal.ZERO;
        if (totalFrais.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Compte caisseOrg = compteService.getCompteOrg(orgId, TypeCompte.CAISSE);
        Compte interetOrg = compteService.ensureCompteOrganisationInteret(orgId);
        mouvements.add(addMouvement(operation, caisseOrg.getId(), SensMouvement.DEBIT, totalFrais));
        compteService.appliquerMouvement(caisseOrg.getId(), SensMouvement.DEBIT, totalFrais, false);
        mouvements.add(addMouvement(operation, interetOrg.getId(), SensMouvement.CREDIT, totalFrais));
        compteService.appliquerMouvement(interetOrg.getId(), SensMouvement.CREDIT, totalFrais);
        String obs = "Frais / intérêts emprunt #" + emprunt.getId() + " → compte intérêts";
        operation.setObservation(
                operation.getObservation() == null ? obs : operation.getObservation() + " · " + obs);
    }

    private StatutEcheance calculerStatutEcheance(Echeance echeance) {
        if (echeance.getMontantPaye().compareTo(echeance.getMontantEcheance()) >= 0) {
            return StatutEcheance.PAYE;
        }
        if (echeance.getMontantPaye().compareTo(BigDecimal.ZERO) > 0) {
            return StatutEcheance.PARTIEL;
        }
        return StatutEcheance.A_PAYER;
    }

    private BigDecimal resolveMontant(RembourserRequest request) {
        if (request.getMontant() != null) {
            return request.getMontant();
        }
        if (request.getMontantCapital() != null) {
            return request.getMontantCapital();
        }
        throw new BusinessException("Montant requis");
    }

    /** Compte membre crédité lors d'un remboursement (selon le type d'emprunt). */
    private Compte compteMembrePourRemboursement(Emprunt emprunt) {
        TypeCompte type = switch (emprunt.getTypeEmprunt()) {
            case SOLIDARITE -> TypeCompte.SOLIDARITE;
            case CAISSE -> TypeCompte.EPARGNE_HEBDO;
            case ETALE -> TypeCompte.EPARGNE_MOIS;
        };
        return compteService.getCompteMembre(emprunt.getMembreId(), type);
    }

    private Echeance resolveEcheance(Emprunt emprunt, Long echeanceId) {
        if (echeanceId == null) {
            return null;
        }
        return echeanceRepository.findByIdAndEmpruntId(echeanceId, emprunt.getId())
                .orElseThrow(() -> new BusinessException("Échéance introuvable"));
    }

    private BigDecimal resoudrePenalite(Emprunt emprunt, RembourserRequest request, RegleOperation regle) {
        if (Boolean.FALSE.equals(request.getAppliquerPenalite())) {
            return BigDecimal.ZERO;
        }
        BigDecimal attendue = calculerPenaliteAttendue(emprunt, request, regle);
        if (request.getMontantPenalite() != null) {
            return request.getMontantPenalite().max(BigDecimal.ZERO);
        }
        return attendue;
    }

    private BigDecimal calculerPenaliteAttendue(Emprunt emprunt, RembourserRequest request, RegleOperation regle) {
        LocalDate datePaiement = request.getDatePaiement() != null ? request.getDatePaiement() : LocalDate.now();
        Echeance ech = echeancePourPenalite(emprunt, request, datePaiement);
        if (ech == null || !datePaiement.isAfter(ech.getDateEcheance())) {
            return BigDecimal.ZERO;
        }
        BigDecimal base = ech.getMontantEcheance().subtract(ech.getMontantPaye()).max(BigDecimal.ZERO);
        return EmpruntCalculHelper.calculerPenaliteRetard(base, regle, ech.getDateEcheance(), datePaiement);
    }

    private Echeance echeancePourPenalite(Emprunt emprunt, RembourserRequest request, LocalDate datePaiement) {
        if (request.getEcheanceId() != null) {
            return resolveEcheance(emprunt, request.getEcheanceId());
        }
        if (emprunt.getEcheances() == null || emprunt.getEcheances().isEmpty()) {
            return null;
        }
        return emprunt.getEcheances().stream()
                .filter(e -> e.getStatut() != StatutEcheance.PAYE)
                .filter(e -> datePaiement.isAfter(e.getDateEcheance()))
                .min(Comparator.comparing(Echeance::getNumero))
                .orElse(null);
    }

    private void validerRemboursement(Emprunt emprunt, RembourserRequest request, BigDecimal penalite, RegleOperation regle) {
        if (emprunt.getStatut() != StatutEmprunt.EN_COURS) {
            throw new BusinessException("Cet emprunt n'est plus en cours de remboursement");
        }
        BigDecimal montantRemb = montantRemboursementEffectif(emprunt, request);
        if (montantRemb.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Le montant du remboursement doit être positif");
        }
        BigDecimal restantEmprunt = emprunt.getMontantTotal().subtract(emprunt.getMontantRembourse());
        if (montantRemb.compareTo(restantEmprunt) > 0) {
            throw new BusinessException("Le montant dépasse le solde restant de l'emprunt");
        }

        boolean echeancesPrevues = emprunt.getEcheances() != null && !emprunt.getEcheances().isEmpty();
        boolean typeAvecEcheance = emprunt.getTypeEmprunt() == TypeEmprunt.ETALE
                || emprunt.getTypeEmprunt() == TypeEmprunt.CAISSE;
        if (typeAvecEcheance && echeancesPrevues && request.getEcheanceId() == null) {
            throw new BusinessException("Sélectionnez l'échéance à rembourser");
        }

        if (request.getEcheanceId() != null) {
            Echeance ech = resolveEcheance(emprunt, request.getEcheanceId());
            if (ech.getStatut() == StatutEcheance.PAYE) {
                throw new BusinessException("Cette échéance est déjà soldée");
            }
            BigDecimal restEch = ech.getMontantEcheance().subtract(ech.getMontantPaye());
            if (emprunt.getTypeEmprunt() == TypeEmprunt.CAISSE) {
                BigDecimal capital = request.getMontantCapital() != null ? request.getMontantCapital() : BigDecimal.ZERO;
                BigDecimal frais = request.getMontantFrais() != null ? request.getMontantFrais() : BigDecimal.ZERO;
                if (montantRemb.compareTo(restEch) > 0) {
                    throw new BusinessException("Le montant dépasse le reste dû sur cette échéance");
                }
                BigDecimal capMax = EmpruntCalculHelper.capitalNominalRestant(emprunt, ech);
                if (capital.compareTo(capMax) > 0) {
                    throw new BusinessException(
                            "Le capital dépasse le nominal restant de l'échéance (max: " + capMax + ")");
                }
                BigDecimal fraisMax = EmpruntCalculHelper.fraisPartRestant(emprunt, ech);
                if (frais.compareTo(fraisMax) > 0) {
                    throw new BusinessException(
                            "Les frais dépassent la part restante sur l'échéance (max: " + fraisMax + ")");
                }
            } else {
                if (montantRemb.compareTo(restEch) > 0) {
                    throw new BusinessException("Le montant dépasse le reste dû sur cette échéance");
                }
            }
        }

        BigDecimal attendue = calculerPenaliteAttendue(emprunt, request, regle);
        if (penalite.compareTo(BigDecimal.ZERO) > 0 && attendue.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("Aucune pénalité de retard applicable pour cette échéance");
        }
        if (attendue.compareTo(BigDecimal.ZERO) > 0
                && penalite.compareTo(attendue.add(new BigDecimal("2"))) > 0) {
            throw new BusinessException("Montant pénalité supérieur au maximum paramétré (attendu: " + attendue + ")");
        }
    }

    private BigDecimal montantRemboursementEffectif(Emprunt emprunt, RembourserRequest request) {
        if (emprunt.getTypeEmprunt() == TypeEmprunt.CAISSE) {
            BigDecimal capital = request.getMontantCapital() != null ? request.getMontantCapital() : resolveMontant(request);
            BigDecimal frais = request.getMontantFrais() != null ? request.getMontantFrais() : BigDecimal.ZERO;
            return capital.add(frais);
        }
        return resolveMontant(request);
    }
}
