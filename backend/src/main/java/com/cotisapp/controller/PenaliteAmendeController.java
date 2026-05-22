package com.cotisapp.controller;

import com.cotisapp.dto.request.AppliquerSanctionRequest;
import com.cotisapp.dto.response.OperationResponse;
import com.cotisapp.dto.response.PenaliteAmendePanneauResponse;
import com.cotisapp.service.PenaliteAmendeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organisations/{orgId}/penalites-amendes")
@RequiredArgsConstructor
public class PenaliteAmendeController {

    private final PenaliteAmendeService penaliteAmendeService;

    @GetMapping("/panneau")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public PenaliteAmendePanneauResponse panneau(@PathVariable Long orgId) {
        return penaliteAmendeService.panneau(orgId);
    }

    @PostMapping("/appliquer")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public OperationResponse appliquer(
            @PathVariable Long orgId,
            @Valid @RequestBody AppliquerSanctionRequest request) {
        return penaliteAmendeService.appliquer(orgId, request);
    }
}
