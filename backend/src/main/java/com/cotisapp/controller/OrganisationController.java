package com.cotisapp.controller;

import com.cotisapp.dto.request.AdminGieUpsertRequest;
import com.cotisapp.dto.request.CreateOrganisationRequest;
import com.cotisapp.dto.request.UpdateOrganisationRequest;
import com.cotisapp.dto.response.OrganisationResponse;
import com.cotisapp.dto.response.UtilisateurOrgResponse;
import com.cotisapp.service.UtilisateurAccesService;
import com.cotisapp.service.OrganisationLogoStorageService;
import com.cotisapp.service.OrganisationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/organisations")
@RequiredArgsConstructor
public class OrganisationController {

    private final OrganisationService organisationService;
    private final UtilisateurAccesService utilisateurAccesService;

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<OrganisationResponse> lister() {
        return organisationService.listerToutes();
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public OrganisationResponse creer(@Valid @RequestBody CreateOrganisationRequest request) {
        return organisationService.creer(request);
    }

    @GetMapping("/{orgId}")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public OrganisationResponse get(@PathVariable Long orgId) {
        return organisationService.getById(orgId);
    }

    @PutMapping("/{orgId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public OrganisationResponse modifier(
            @PathVariable Long orgId, @Valid @RequestBody UpdateOrganisationRequest request) {
        return organisationService.modifier(orgId, request);
    }

    @PutMapping("/{orgId}/admin-gie")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public UtilisateurOrgResponse enregistrerAdminGie(
            @PathVariable Long orgId, @Valid @RequestBody AdminGieUpsertRequest request) {
        return utilisateurAccesService.upsertAdminGie(orgId, request);
    }

    @DeleteMapping("/{orgId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public void supprimer(@PathVariable Long orgId) {
        organisationService.supprimer(orgId);
    }

    @PostMapping(value = "/{orgId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPERADMIN')")
    public OrganisationResponse uploadLogo(
            @PathVariable Long orgId, @RequestParam("logo") MultipartFile logo) {
        return organisationService.enregistrerLogo(orgId, logo);
    }

    @GetMapping("/{orgId}/logo")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public ResponseEntity<Resource> telechargerLogo(@PathVariable Long orgId) {
        OrganisationLogoStorageService.LogoTelechargement telechargement =
                organisationService.preparerLogo(orgId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, telechargement.typeMime())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(telechargement.resource());
    }
}
