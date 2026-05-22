package com.cotisapp.controller;

import com.cotisapp.dto.response.CotisationAnnulationResponse;
import com.cotisapp.dto.response.CotisationHistoriqueLigneResponse;
import com.cotisapp.dto.response.CotisationPanneauResponse;
import com.cotisapp.service.CotisationAnnulationService;
import com.cotisapp.service.CotisationHistoriqueService;
import com.cotisapp.service.CotisationSuiviService;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organisations/{orgId}/cotisations")
@RequiredArgsConstructor
public class CotisationSuiviController {

    private final CotisationSuiviService cotisationSuiviService;
    private final CotisationHistoriqueService cotisationHistoriqueService;
    private final CotisationAnnulationService cotisationAnnulationService;

    @GetMapping("/panneau")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public CotisationPanneauResponse panneau(
            @PathVariable Long orgId,
            @RequestParam(defaultValue = "hebdo") String t,
            @RequestParam(required = false) String semaine,
            @RequestParam(required = false) String mois) {
        return cotisationSuiviService.panneau(orgId, t, semaine, mois);
    }

    @GetMapping("/historique")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<CotisationHistoriqueLigneResponse> historique(@PathVariable Long orgId) {
        return cotisationHistoriqueService.historique(orgId);
    }

    @PostMapping("/operations/{operationId}/annuler")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public CotisationAnnulationResponse annuler(
            @PathVariable Long orgId,
            @PathVariable Long operationId) {
        return cotisationAnnulationService.annuler(orgId, operationId);
    }
}
