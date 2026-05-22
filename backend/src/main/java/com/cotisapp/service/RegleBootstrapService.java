package com.cotisapp.service;

import com.cotisapp.repository.RegleOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée les règles manquantes dans une transaction en écriture (évite les erreurs read-only).
 */
@Service
@RequiredArgsConstructor
public class RegleBootstrapService {

    private final RegleOperationRepository regleOperationRepository;
    private final RegleInitialisationService regleInitialisationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assurerReglesPourOrganisation(Long organisationId) {
        if (regleOperationRepository.findByOrganisationId(organisationId).isEmpty()) {
            regleInitialisationService.initialiserReglesParDefaut(organisationId);
        } else {
            regleInitialisationService.assurerReglesCotisation(organisationId);
        }
    }
}
