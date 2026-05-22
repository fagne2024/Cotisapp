package com.cotisapp.controller;

import com.cotisapp.dto.request.UpdateRegleOperationRequest;
import com.cotisapp.dto.response.CotisationsReglesResponse;
import com.cotisapp.dto.response.EmpruntsReglesResponse;
import com.cotisapp.dto.response.RegleOperationResponse;
import com.cotisapp.service.RegleOperationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organisations/{orgId}/regles")
@RequiredArgsConstructor
public class RegleOperationController {

    private final RegleOperationService regleOperationService;

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public List<RegleOperationResponse> lister(@PathVariable Long orgId) {
        return regleOperationService.lister(orgId);
    }

    @GetMapping("/cotisations")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public CotisationsReglesResponse cotisations(@PathVariable Long orgId) {
        return regleOperationService.obtenirReglesCotisations(orgId);
    }

    @GetMapping("/emprunts")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public EmpruntsReglesResponse emprunts(@PathVariable Long orgId) {
        return regleOperationService.obtenirReglesEmprunts(orgId);
    }

    @PutMapping("/{regleId}")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public RegleOperationResponse mettreAJour(
            @PathVariable Long orgId,
            @PathVariable Long regleId,
            @Valid @RequestBody UpdateRegleOperationRequest request) {
        return regleOperationService.mettreAJour(orgId, regleId, request);
    }

    @PatchMapping("/{regleId}/actif")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public RegleOperationResponse basculerActif(
            @PathVariable Long orgId,
            @PathVariable Long regleId,
            @RequestBody Map<String, Boolean> body) {
        boolean actif = Boolean.TRUE.equals(body.get("actif"));
        return regleOperationService.basculerActif(orgId, regleId, actif);
    }

    @PostMapping("/reinitialiser")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public List<RegleOperationResponse> reinitialiser(@PathVariable Long orgId) {
        return regleOperationService.reinitialiser(orgId);
    }
}
