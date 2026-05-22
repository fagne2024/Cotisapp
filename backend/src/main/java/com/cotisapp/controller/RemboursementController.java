package com.cotisapp.controller;

import com.cotisapp.dto.response.CotisationAnnulationResponse;
import com.cotisapp.dto.response.RemboursementHistoriqueLigneResponse;
import com.cotisapp.dto.response.RemboursementPanneauResponse;
import com.cotisapp.service.RemboursementAnnulationService;
import com.cotisapp.service.RemboursementHistoriqueService;
import com.cotisapp.service.RemboursementPanneauService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organisations/{orgId}/remboursements")
@RequiredArgsConstructor
public class RemboursementController {

    private final RemboursementHistoriqueService remboursementHistoriqueService;
    private final RemboursementPanneauService remboursementPanneauService;
    private final RemboursementAnnulationService remboursementAnnulationService;

    @GetMapping("/panneau")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public RemboursementPanneauResponse panneau(@PathVariable Long orgId) {
        return remboursementPanneauService.panneau(orgId);
    }

    @GetMapping("/historique")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<RemboursementHistoriqueLigneResponse> historique(@PathVariable Long orgId) {
        return remboursementHistoriqueService.historique(orgId);
    }

    @PostMapping("/operations/{operationId}/annuler")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public CotisationAnnulationResponse annuler(
            @PathVariable Long orgId,
            @PathVariable Long operationId) {
        return remboursementAnnulationService.annuler(orgId, operationId);
    }
}
