package com.cotisapp.service;

import com.cotisapp.domain.entity.JourneeReunion;
import com.cotisapp.domain.entity.Organisation;
import com.cotisapp.domain.enums.StatutPlanad;
import com.cotisapp.repository.JourneeReunionRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Ouverture / création des PLANAD (journées de réunion) sans cycle avec {@link RecapJourneeService}.
 */
@Service
@RequiredArgsConstructor
public class PlanadOuvertureService {

    private final JourneeReunionRepository journeeReunionRepository;
    private final OperationRepository operationRepository;
    private final OrganisationRepository organisationRepository;

    /**
     * Après clôture d'un PLANAD : ouvre le suivant sur la date de la première opération
     * postérieure à la date de réunion clôturée (s'il en existe une).
     */
    @Transactional
    public Optional<JourneeReunion> ouvrirPlanadSuivantApresCloture(
            Long orgId, Long exerciceId, LocalDate dateReunionCloturee) {
        if (journeeReunionRepository.findPlanadOuvert(exerciceId).isPresent()) {
            return Optional.empty();
        }
        return operationRepository
                .findMinDateOperationApres(orgId, exerciceId, dateReunionCloturee)
                .map(date -> assurerPlanadOuvertSurDate(orgId, exerciceId, date));
    }

    /**
     * Si aucun PLANAD n'est ouvert, crée (ou réactive) un PLANAD sur la date d'opération concernée.
     */
    @Transactional
    public JourneeReunion assurerPlanadOuvertPourDate(Long orgId, Long exerciceId, LocalDate dateOperation) {
        if (journeeReunionRepository.findPlanadOuvert(exerciceId).isPresent()) {
            return journeeReunionRepository.findPlanadOuvert(exerciceId).orElseThrow();
        }
        return assurerPlanadOuvertSurDate(orgId, exerciceId, dateOperation);
    }

    @Transactional
    public JourneeReunion creerPlanadOuvert(Long orgId, Long exerciceId, LocalDate dateReunion) {
        return assurerPlanadOuvertSurDate(orgId, exerciceId, dateReunion);
    }

    private JourneeReunion assurerPlanadOuvertSurDate(Long orgId, Long exerciceId, LocalDate dateReunion) {
        Optional<JourneeReunion> existante =
                journeeReunionRepository.findByExerciceIdAndDateReunion(exerciceId, dateReunion);
        if (existante.isPresent()) {
            JourneeReunion j = existante.get();
            if (j.getStatut() == StatutPlanad.CLOTURE) {
                j.setStatut(StatutPlanad.OUVERT);
                j.setDateCloture(null);
                return journeeReunionRepository.save(j);
            }
            return j;
        }
        Organisation org = organisationRepository.findById(orgId).orElseThrow();
        int numero = journeeReunionRepository.findMaxNumero(exerciceId) + 1;
        return journeeReunionRepository.save(JourneeReunion.builder()
                .organisationId(orgId)
                .exerciceId(exerciceId)
                .numero(numero)
                .dateReunion(dateReunion)
                .libelle(RecapJourneeService.libelleJournee(org.getCode(), numero))
                .statut(StatutPlanad.OUVERT)
                .build());
    }
}
