package com.cotisapp.controller;

import com.cotisapp.dto.request.CreateCompteModeleMembreRequest;
import com.cotisapp.dto.response.CompteModeleMembreResponse;
import com.cotisapp.service.CompteModeleMembreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations/{orgId}/comptes-modeles-membre")
@RequiredArgsConstructor
public class CompteModeleMembreController {

    private final CompteModeleMembreService compteModeleMembreService;

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public List<CompteModeleMembreResponse> lister(
            @PathVariable Long orgId,
            @RequestParam(value = "actifsSeulement", defaultValue = "true") boolean actifsSeulement) {
        return compteModeleMembreService.lister(orgId, actifsSeulement);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public CompteModeleMembreResponse creer(
            @PathVariable Long orgId, @Valid @RequestBody CreateCompteModeleMembreRequest request) {
        return compteModeleMembreService.creer(orgId, request);
    }
}
