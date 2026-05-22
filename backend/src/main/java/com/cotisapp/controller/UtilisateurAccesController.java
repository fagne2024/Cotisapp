package com.cotisapp.controller;

import com.cotisapp.domain.enums.Role;
import com.cotisapp.dto.request.CreateUtilisateurOrgRequest;
import com.cotisapp.dto.response.UtilisateurAccesStatsResponse;
import com.cotisapp.dto.response.UtilisateurOrgResponse;
import com.cotisapp.service.UtilisateurAccesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organisations/{orgId}/utilisateurs-acces")
@RequiredArgsConstructor
public class UtilisateurAccesController {

    private final UtilisateurAccesService utilisateurAccesService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public UtilisateurAccesStatsResponse statistiques(@PathVariable Long orgId) {
        return utilisateurAccesService.statistiques(orgId);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public List<UtilisateurOrgResponse> lister(
            @PathVariable Long orgId,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean actif) {
        return utilisateurAccesService.lister(orgId, role, actif);
    }

    @PostMapping
    @PreAuthorize("@orgSecurityService.peutGestionOrg(#orgId)")
    public UtilisateurOrgResponse creer(
            @PathVariable Long orgId,
            @Valid @RequestBody CreateUtilisateurOrgRequest request) {
        return utilisateurAccesService.creer(orgId, request);
    }

    @PatchMapping("/{utilisateurId}/actif")
    @PreAuthorize("@orgSecurityService.peutGestionOrg(#orgId)")
    public UtilisateurOrgResponse basculerActif(
            @PathVariable Long orgId,
            @PathVariable Long utilisateurId,
            @RequestBody Map<String, Boolean> body) {
        return utilisateurAccesService.basculerActif(orgId, utilisateurId, Boolean.TRUE.equals(body.get("actif")));
    }
}
