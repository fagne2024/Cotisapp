package com.cotisapp.service;

import com.cotisapp.domain.entity.Echeance;
import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.StatutEcheance;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.response.CotisationAnnulationResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.EcheanceRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RemboursementAnnulationService {

    private final OperationRepository operationRepository;
    private final EmpruntRepository empruntRepository;
    private final EcheanceRepository echeanceRepository;
    private final OperationContrepassationService contrepassationService;
    private final JournalService journalService;
    private final MembreRepository membreRepository;

    @Transactional
    public CotisationAnnulationResponse annuler(Long orgId, Long operationId) {
        Operation origine = operationRepository.findByIdAndOrganisationId(operationId, orgId)
                .orElseThrow(() -> new BusinessException("Opération introuvable"));

        if (origine.getEmpruntId() == null) {
            throw new BusinessException("Emprunt associé introuvable pour ce remboursement");
        }

        validerDernierRemboursement(origine);

        Emprunt emprunt = empruntRepository.findWithEcheancesByIdAndOrganisationId(origine.getEmpruntId(), orgId)
                .orElseThrow(() -> new BusinessException("Emprunt introuvable"));

        if (emprunt.getStatut() == StatutEmprunt.ANNULE) {
            throw new BusinessException("L'emprunt lié est annulé");
        }

        CotisationAnnulationResponse res = contrepassationService.contrepasser(
                orgId, operationId, TypeOperation.REMBOURSEMENT);

        BigDecimal montantSurEmprunt = montantAffecteEmprunt(origine);
        emprunt.setMontantRembourse(emprunt.getMontantRembourse().subtract(montantSurEmprunt).max(BigDecimal.ZERO));
        BigDecimal partCaisseAnnulee = EmpruntAvanceCaisseHelper.extrairePartCaisseRemboursement(origine.getObservation());
        if (partCaisseAnnulee.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dejaRemb = emprunt.getMontantRembourseAvanceCaisse() != null
                    ? emprunt.getMontantRembourseAvanceCaisse() : BigDecimal.ZERO;
            emprunt.setMontantRembourseAvanceCaisse(dejaRemb.subtract(partCaisseAnnulee).max(BigDecimal.ZERO));
        }
        if (emprunt.getMontantRembourse().compareTo(emprunt.getMontantTotal()) < 0) {
            emprunt.setStatut(StatutEmprunt.EN_COURS);
        }
        annulerPaiementEcheances(emprunt, origine, montantSurEmprunt);
        empruntRepository.save(emprunt);

        Membre membre = membreRepository.findById(emprunt.getMembreId()).orElse(null);
        String cible = membre != null
                ? JournalModificationFormatter.cibleMembre(
                        membre.getCodeMembre(), membre.getPrenom(), membre.getNom(), membre.getId())
                : "membre n°" + emprunt.getMembreId();
        journalService.enregistrer(
                orgId,
                "ANNULATION_REMBOURSEMENT",
                "Annulation remboursement — "
                        + cible
                        + " — "
                        + JournalModificationFormatter.montantFcfa(origine.getMontant())
                        + " — emprunt n°"
                        + emprunt.getId()
                        + " — op. n°"
                        + operationId
                        + " → contre-passation n°"
                        + res.getOperationAnnulationId());

        return CotisationAnnulationResponse.builder()
                .operationOrigineId(res.getOperationOrigineId())
                .operationAnnulationId(res.getOperationAnnulationId())
                .dateAnnulation(res.getDateAnnulation())
                .mouvementsInverses(res.getMouvementsInverses())
                .message("Remboursement annulé — " + res.getMouvementsInverses() + " mouvement(s) inversé(s).")
                .build();
    }

    private void validerDernierRemboursement(Operation origine) {
        List<Operation> remboursements = operationRepository
                .findByEmpruntIdAndTypeOperationAndAnnuleeFalseAndOperationOrigineIdIsNullOrderByDateOperationDescDateCreationDesc(
                        origine.getEmpruntId(), TypeOperation.REMBOURSEMENT);
        if (remboursements.isEmpty()) {
            throw new BusinessException("Aucun remboursement actif trouvé pour cet emprunt");
        }
        Operation dernier = remboursements.get(0);
        if (!Objects.equals(dernier.getId(), origine.getId())) {
            throw new BusinessException(
                    "Seul le dernier remboursement de l'emprunt peut être annulé (annulez les plus récents d'abord)");
        }
    }

    private BigDecimal montantAffecteEmprunt(Operation op) {
        BigDecimal capital = op.getMontant() != null ? op.getMontant() : BigDecimal.ZERO;
        BigDecimal frais = op.getMontantFrais() != null ? op.getMontantFrais() : BigDecimal.ZERO;
        return capital.add(frais);
    }

    private void annulerPaiementEcheances(Emprunt emprunt, Operation op, BigDecimal montant) {
        if (emprunt.getEcheances() == null || emprunt.getEcheances().isEmpty()) {
            return;
        }
        if (op.getEcheanceId() != null) {
            echeanceRepository.findByIdAndEmpruntId(op.getEcheanceId(), emprunt.getId())
                    .ifPresent(ech -> retirerPaiementEcheance(ech, montant));
            return;
        }
        BigDecimal restant = montant;
        List<Echeance> parNumeroDesc = emprunt.getEcheances().stream()
                .sorted(Comparator.comparing(Echeance::getNumero).reversed())
                .toList();
        for (Echeance ech : parNumeroDesc) {
            if (restant.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (ech.getMontantPaye().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal retrait = restant.min(ech.getMontantPaye());
            retirerPaiementEcheance(ech, retrait);
            restant = restant.subtract(retrait);
        }
    }

    private void retirerPaiementEcheance(Echeance ech, BigDecimal montant) {
        BigDecimal nouveauPaye = ech.getMontantPaye().subtract(montant).max(BigDecimal.ZERO);
        ech.setMontantPaye(nouveauPaye);
        if (nouveauPaye.compareTo(BigDecimal.ZERO) <= 0) {
            ech.setStatut(StatutEcheance.A_PAYER);
            ech.setDatePaiement(null);
        } else if (nouveauPaye.compareTo(ech.getMontantEcheance()) >= 0) {
            ech.setStatut(StatutEcheance.PAYE);
        } else {
            ech.setStatut(StatutEcheance.PARTIEL);
        }
        echeanceRepository.save(ech);
    }
}
