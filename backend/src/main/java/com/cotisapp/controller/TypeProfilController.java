package com.cotisapp.controller;

import com.cotisapp.dto.request.CreateTypeProfilRequest;
import com.cotisapp.dto.request.SauvegarderTypeProfilDroitsRequest;
import com.cotisapp.dto.request.UpdateTypeProfilRequest;
import com.cotisapp.dto.response.ActionDroitResponse;
import com.cotisapp.dto.response.TypeProfilDroitResponse;
import com.cotisapp.dto.response.TypeProfilResponse;
import com.cotisapp.service.TypeProfilDroitService;
import com.cotisapp.service.TypeProfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations/{orgId}/types-profil")
@RequiredArgsConstructor
public class TypeProfilController {

    private final TypeProfilService typeProfilService;
    private final TypeProfilDroitService typeProfilDroitService;

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public List<TypeProfilResponse> lister(@PathVariable Long orgId) {
        return typeProfilService.listerPourOrganisation(orgId);
    }

    @GetMapping("/gestion")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public List<TypeProfilResponse> listerGestion(@PathVariable Long orgId) {
        return typeProfilService.listerGestionOrganisation(orgId);
    }

    @GetMapping("/actions")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public List<ActionDroitResponse> listerActions() {
        return typeProfilDroitService.listerCatalogue();
    }

    @GetMapping("/{typeProfilId}/droits")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public List<TypeProfilDroitResponse> listerDroits(
            @PathVariable Long orgId, @PathVariable Long typeProfilId) {
        return typeProfilDroitService.listerDroitsTypeProfil(orgId, typeProfilId);
    }

    @PutMapping("/{typeProfilId}/droits")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public List<TypeProfilDroitResponse> sauvegarderDroits(
            @PathVariable Long orgId,
            @PathVariable Long typeProfilId,
            @Valid @RequestBody SauvegarderTypeProfilDroitsRequest request) {
        return typeProfilDroitService.sauvegarderDroits(orgId, typeProfilId, request.getDroits());
    }

    @PostMapping("/systeme/reinitialiser-droits")
    @PreAuthorize(
            "hasRole('SUPERADMIN') or (hasRole('ADMIN_GIE') and @orgSecurityService.belongsTo(#orgId))")
    public void reinitialiserDroitsSysteme(@PathVariable Long orgId) {
        typeProfilDroitService.reinitialiserDroitsProfilsSysteme(orgId);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public TypeProfilResponse creer(
            @PathVariable Long orgId, @Valid @RequestBody CreateTypeProfilRequest request) {
        return typeProfilService.creer(orgId, request);
    }

    @PatchMapping("/{typeProfilId}")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public TypeProfilResponse mettreAJour(
            @PathVariable Long orgId,
            @PathVariable Long typeProfilId,
            @Valid @RequestBody UpdateTypeProfilRequest request) {
        return typeProfilService.mettreAJour(orgId, typeProfilId, request);
    }

    @DeleteMapping("/{typeProfilId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public void supprimer(@PathVariable Long orgId, @PathVariable Long typeProfilId) {
        typeProfilService.supprimer(orgId, typeProfilId);
    }
}
