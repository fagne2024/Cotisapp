package com.cotisapp.controller;

import com.cotisapp.dto.response.MesDroitsResponse;
import com.cotisapp.service.MesDroitsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organisations/{orgId}/mes-droits")
@RequiredArgsConstructor
public class MesDroitsController {

    private final MesDroitsService mesDroitsService;

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canAccessOrg(#orgId)")
    public MesDroitsResponse mesDroits(@PathVariable Long orgId) {
        return mesDroitsService.chargerPourOrganisation(orgId);
    }
}
