package com.cotisapp.controller;

import com.cotisapp.dto.response.CompteReleveResponse;
import com.cotisapp.dto.response.CompteReleveSyntheseResponse;
import com.cotisapp.service.CompteReleveService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/organisations/{orgId}/comptes-releves")
@RequiredArgsConstructor
public class CompteReleveController {

    private final CompteReleveService compteReleveService;

    @GetMapping("/synthese")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public CompteReleveSyntheseResponse synthese(@PathVariable Long orgId) {
        return compteReleveService.chargerSynthese(orgId);
    }

    @GetMapping("/releve")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public CompteReleveResponse releve(
            @PathVariable Long orgId,
            @RequestParam(defaultValue = "caisse") String scope,
            @RequestParam(required = false) Long compteId,
            @RequestParam(required = false) Long membreId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String q) {
        return compteReleveService.chargerReleve(orgId, scope, compteId, membreId, dateDebut, dateFin, type, statut, q);
    }
}
