package com.cotisapp.controller;

import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.dto.request.AccorderEmpruntRequest;
import com.cotisapp.dto.response.EmpruntResponse;
import com.cotisapp.dto.response.EmpruntHistoriqueLigneResponse;
import com.cotisapp.dto.response.CotisationAnnulationResponse;
import com.cotisapp.service.AccorderEmpruntService;
import com.cotisapp.service.EmpruntAnnulationService;
import com.cotisapp.service.EmpruntHistoriqueService;
import com.cotisapp.service.EmpruntService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations/{orgId}/emprunts")
@RequiredArgsConstructor
public class EmpruntListController {

    private final EmpruntService empruntService;
    private final AccorderEmpruntService accorderEmpruntService;
    private final EmpruntHistoriqueService empruntHistoriqueService;
    private final EmpruntAnnulationService empruntAnnulationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public EmpruntResponse accorder(
            @PathVariable Long orgId, @Valid @RequestBody AccorderEmpruntRequest request) {
        return accorderEmpruntService.accorder(orgId, request);
    }

    @GetMapping
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<EmpruntResponse> lister(
            @PathVariable Long orgId,
            @RequestParam(required = false) TypeEmprunt type) {
        return empruntService.lister(orgId, type);
    }

    @GetMapping("/suivi")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<EmpruntResponse> listerPourSuivi(
            @PathVariable Long orgId,
            @RequestParam(required = false) TypeEmprunt type) {
        return empruntService.listerPourSuivi(orgId, type);
    }

    @GetMapping("/historique")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<EmpruntHistoriqueLigneResponse> historique(@PathVariable Long orgId) {
        return empruntHistoriqueService.historique(orgId);
    }

    @PostMapping("/operations/{operationId}/annuler")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public CotisationAnnulationResponse annulerOctroi(
            @PathVariable Long orgId,
            @PathVariable Long operationId) {
        return empruntAnnulationService.annuler(orgId, operationId);
    }

    @GetMapping("/{empId}")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public EmpruntResponse get(
            @PathVariable Long orgId,
            @PathVariable Long empId) {
        return empruntService.getById(orgId, empId);
    }
}
