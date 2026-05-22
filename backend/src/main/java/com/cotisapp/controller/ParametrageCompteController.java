package com.cotisapp.controller;

import com.cotisapp.dto.request.UpdateParametrageComptesRequest;
import com.cotisapp.dto.response.ParametrageCompteResponse;
import com.cotisapp.service.ParametrageCompteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations/{orgId}/parametrage-comptes")
@RequiredArgsConstructor
public class ParametrageCompteController {

    private final ParametrageCompteService parametrageCompteService;

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public List<ParametrageCompteResponse> lister(@PathVariable Long orgId) {
        return parametrageCompteService.lister(orgId);
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public List<ParametrageCompteResponse> mettreAJour(
            @PathVariable Long orgId,
            @Valid @RequestBody UpdateParametrageComptesRequest request) {
        return parametrageCompteService.mettreAJour(orgId, request);
    }
}
