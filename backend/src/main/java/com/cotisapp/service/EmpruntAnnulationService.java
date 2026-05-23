package com.cotisapp.service;

import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.response.CotisationAnnulationResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmpruntAnnulationService {

    private final OperationRepository operationRepository;
    private final EmpruntRepository empruntRepository;
    private final OperationContrepassationService contrepassationService;
    private final JournalService journalService;
    private final MembreRepository membreRepository;

    @Transactional
    public CotisationAnnulationResponse annuler(Long orgId, Long operationId) {
        Operation origine = operationRepository.findByIdAndOrganisationId(operationId, orgId)
                .orElseThrow(() -> new BusinessException("Opération introuvable"));

        if (origine.getEmpruntId() == null) {
            throw new BusinessException("Emprunt associé introuvable pour cette opération");
        }

        Emprunt emprunt = empruntRepository.findWithEcheancesByIdAndOrganisationId(origine.getEmpruntId(), orgId)
                .orElseThrow(() -> new BusinessException("Emprunt introuvable"));

        if (emprunt.getStatut() == StatutEmprunt.ANNULE) {
            throw new BusinessException("Cet emprunt est déjà annulé");
        }

        if (operationRepository.existsByEmpruntIdAndTypeOperationAndAnnuleeFalseAndOperationOrigineIdIsNull(
                emprunt.getId(), TypeOperation.REMBOURSEMENT)) {
            throw new BusinessException(
                    "Impossible d'annuler l'octroi : des remboursements existent sur cet emprunt. Annulez-les d'abord.");
        }

        CotisationAnnulationResponse res = contrepassationService.contrepasser(
                orgId, operationId, TypeOperation.EMPRUNT);

        emprunt.setStatut(StatutEmprunt.ANNULE);
        empruntRepository.save(emprunt);

        Membre membre = membreRepository.findById(emprunt.getMembreId()).orElse(null);
        String cible = membre != null
                ? JournalModificationFormatter.cibleMembre(
                        membre.getCodeMembre(), membre.getPrenom(), membre.getNom(), membre.getId())
                : "membre n°" + emprunt.getMembreId();
        journalService.enregistrer(
                orgId,
                "ANNULATION_EMPRUNT",
                "Annulation octroi emprunt "
                        + emprunt.getTypeEmprunt().name()
                        + " — "
                        + cible
                        + " — capital "
                        + JournalModificationFormatter.montantFcfa(emprunt.getMontantTotal())
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
                .message("Emprunt annulé — " + res.getMouvementsInverses() + " mouvement(s) inversé(s).")
                .build();
    }
}
