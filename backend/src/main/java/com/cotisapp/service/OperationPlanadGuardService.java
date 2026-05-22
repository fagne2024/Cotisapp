package com.cotisapp.service;

import com.cotisapp.domain.entity.JourneeReunion;
import com.cotisapp.domain.enums.StatutPlanad;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.JourneeReunionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class OperationPlanadGuardService {

    private final JourneeReunionRepository journeeReunionRepository;

    @Transactional(readOnly = true)
    public void verifierDateOperationAutorisee(Long orgId, Long exerciceId, LocalDate dateOperation) {
        verifierDateOperationAutorisee(orgId, exerciceId, dateOperation, true);
    }

    /**
     * @param lierAuPlanadOuvert si {@code true}, la date doit être celle du PLANAD ouvert (cotisations, emprunts…).
     *                           si {@code false}, toute date est autorisée sauf un PLANAD clôturé à cette date
     *                           (remboursements).
     */
    @Transactional(readOnly = true)
    public void verifierDateOperationAutorisee(
            Long orgId, Long exerciceId, LocalDate dateOperation, boolean lierAuPlanadOuvert) {
        if (dateOperation == null) {
            throw new BusinessException("La date d'opération est obligatoire");
        }

        journeeReunionRepository.findByExerciceIdAndDateReunion(exerciceId, dateOperation)
                .ifPresent(j -> {
                    if (j.getStatut() == StatutPlanad.CLOTURE) {
                        throw new BusinessException(
                                "Le " + j.getLibelle() + " est clôturé — aucune opération n'est possible pour cette date.");
                    }
                });

        if (!lierAuPlanadOuvert) {
            return;
        }

        journeeReunionRepository.findPlanadOuvert(exerciceId).ifPresent(ouvert -> {
            if (!ouvert.getDateReunion().equals(dateOperation)) {
                throw new BusinessException(
                        "Clôturez d'abord le " + ouvert.getLibelle()
                                + " avant d'enregistrer des opérations sur une autre date.");
            }
        });
    }

    @Transactional(readOnly = true)
    public void verifierPeutOuvrirNouveauPlanad(Long exerciceId) {
        journeeReunionRepository.findPlanadOuvert(exerciceId).ifPresent(ouvert -> {
            throw new BusinessException(
                    "Clôturez le " + ouvert.getLibelle() + " avant d'ouvrir un nouveau PLANAD.");
        });
    }
}
