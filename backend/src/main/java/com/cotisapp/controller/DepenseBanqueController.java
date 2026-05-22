package com.cotisapp.controller;

import com.cotisapp.dto.request.BanqueMouvementRequest;
import com.cotisapp.dto.request.DepenseRequest;
import com.cotisapp.dto.response.DepenseBanqueDashboardResponse;
import com.cotisapp.dto.response.OperationResponse;
import com.cotisapp.service.DepenseBanqueService;
import com.cotisapp.service.ReleveBancaireStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/organisations/{orgId}/depenses-banque")
@RequiredArgsConstructor
public class DepenseBanqueController {

    private final DepenseBanqueService depenseBanqueService;
    private final ReleveBancaireStorageService releveBancaireStorageService;

    @GetMapping
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public DepenseBanqueDashboardResponse tableauDeBord(@PathVariable Long orgId) {
        return depenseBanqueService.chargerTableauDeBord(orgId);
    }

    @PostMapping("/depenses")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public OperationResponse enregistrerDepense(
            @PathVariable Long orgId,
            @Valid @RequestBody DepenseRequest request) {
        return depenseBanqueService.enregistrerDepense(orgId, request);
    }

    @PostMapping(value = "/banque", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public OperationResponse enregistrerBanque(
            @PathVariable Long orgId,
            @Valid @RequestPart("data") BanqueMouvementRequest request,
            @RequestPart(value = "releve", required = false) MultipartFile releve) {
        return depenseBanqueService.enregistrerBanque(orgId, request, releve);
    }

    @GetMapping("/releves/{releveId}")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public ResponseEntity<Resource> telechargerReleve(
            @PathVariable Long orgId,
            @PathVariable Long releveId) {
        var telechargement = releveBancaireStorageService.preparerTelechargement(orgId, releveId);
        MediaType mediaType = telechargement.typeMime() != null
                ? MediaType.parseMediaType(telechargement.typeMime())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + telechargement.nomFichier() + "\"")
                .contentType(mediaType)
                .body(telechargement.resource());
    }
}
