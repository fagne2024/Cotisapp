package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.MouvementCompte;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.response.CotisationAnnulationResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.security.OrganisationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Contre-passation comptable d'une opération (mouvements inversés).
 */
@Service
@RequiredArgsConstructor
public class OperationContrepassationService {

    private final OperationRepository operationRepository;
    private final CompteRepository compteRepository;
    private final CompteService compteService;
    private final OperationPlanadGuardService operationPlanadGuardService;

    @Transactional
    public CotisationAnnulationResponse contrepasser(
            Long orgId,
            Long operationId,
            TypeOperation typeAttendu) {
        Operation origine = operationRepository.findByIdAndOrganisationId(operationId, orgId)
                .orElseThrow(() -> new BusinessException("Opération introuvable"));

        if (origine.getOperationOrigineId() != null) {
            throw new BusinessException("Impossible d'annuler une opération de contre-passation");
        }
        if (Boolean.TRUE.equals(origine.getAnnulee())) {
            throw new BusinessException("Cette opération est déjà annulée");
        }
        if (operationRepository.existsByOperationOrigineId(operationId)) {
            throw new BusinessException("Cette opération est déjà annulée");
        }
        if (origine.getTypeOperation() != typeAttendu) {
            throw new BusinessException("Type d'opération incompatible avec l'annulation demandée");
        }

        origine.getMouvements().size();
        if (origine.getMouvements().isEmpty()) {
            throw new BusinessException("Aucun mouvement comptable à inverser pour cette opération");
        }

        boolean lierPlanad = typeAttendu != TypeOperation.REMBOURSEMENT;
        operationPlanadGuardService.verifierDateOperationAutorisee(
                orgId, origine.getExerciceId(), origine.getDateOperation(), lierPlanad);
        LocalDate dateAnnulation = origine.getDateOperation();
        String obsAnnulation = "[ANNULATION] Contre-passation opération #" + operationId
                + (origine.getObservation() != null ? " — " + origine.getObservation() : "");

        Operation annulation = Operation.builder()
                .organisationId(orgId)
                .exerciceId(origine.getExerciceId())
                .membreId(origine.getMembreId())
                .typeOperation(origine.getTypeOperation())
                .montant(origine.getMontant())
                .montantFrais(origine.getMontantFrais())
                .dateOperation(dateAnnulation)
                .observation(obsAnnulation)
                .moisAnnee(origine.getMoisAnnee())
                .empruntId(origine.getEmpruntId())
                .echeanceId(origine.getEcheanceId())
                .operationOrigineId(operationId)
                .utilisateurId(OrganisationContext.getUserId())
                .build();

        List<MouvementCompte> mouvementsInverses = new ArrayList<>();
        for (MouvementCompte mc : origine.getMouvements()) {
            SensMouvement sensInverse = mc.getSens() == SensMouvement.CREDIT
                    ? SensMouvement.DEBIT
                    : SensMouvement.CREDIT;
            Compte compte = compteRepository.findById(mc.getCompteId())
                    .orElseThrow(() -> new BusinessException("Compte introuvable pour le mouvement"));
            boolean autoriserNegatif = compte.getProprietaire() == ProprietaireCompte.MEMBRE;

            MouvementCompte inverse = MouvementCompte.builder()
                    .operation(annulation)
                    .compteId(mc.getCompteId())
                    .sens(sensInverse)
                    .montant(mc.getMontant())
                    .build();
            annulation.getMouvements().add(inverse);
            mouvementsInverses.add(inverse);
            compteService.appliquerMouvement(mc.getCompteId(), sensInverse, mc.getMontant(), autoriserNegatif);
        }

        Operation saved = operationRepository.save(annulation);
        origine.setAnnulee(true);
        operationRepository.save(origine);

        return CotisationAnnulationResponse.builder()
                .operationOrigineId(operationId)
                .operationAnnulationId(saved.getId())
                .dateAnnulation(dateAnnulation)
                .mouvementsInverses(mouvementsInverses.size())
                .message(mouvementsInverses.size() + " mouvement(s) inversé(s) sur les comptes.")
                .build();
    }
}
