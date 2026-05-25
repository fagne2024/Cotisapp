package com.cotisapp.controller;

import com.cotisapp.dto.request.ParametrageClotureExerciceRequest;
import com.cotisapp.dto.response.ParametrageClotureExerciceResponse;
import com.cotisapp.dto.response.PreviewClotureExerciceResponse;
import com.cotisapp.service.ClotureExerciceRepartitionService;
import com.cotisapp.service.ParametrageClotureExerciceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organisations/{orgId}/parametrage-cloture")
@RequiredArgsConstructor
public class ParametrageClotureExerciceController {

    private final ParametrageClotureExerciceService parametrageClotureExerciceService;
    private final ClotureExerciceRepartitionService clotureExerciceRepartitionService;

    @GetMapping
    @PreAuthorize("@orgSecurityService.peutGestionOrg(#orgId)")
    public ParametrageClotureExerciceResponse get(@PathVariable Long orgId) {
        return parametrageClotureExerciceService.get(orgId);
    }

    @PutMapping
    @PreAuthorize("@orgSecurityService.peutGestionOrg(#orgId)")
    public ParametrageClotureExerciceResponse enregistrer(
            @PathVariable Long orgId, @Valid @RequestBody ParametrageClotureExerciceRequest request) {
        return parametrageClotureExerciceService.enregistrer(orgId, request);
    }

    @PostMapping("/preview-repartition")
    @PreAuthorize("@orgSecurityService.peutGestionOrg(#orgId)")
    public PreviewClotureExerciceResponse previewRepartition(
            @PathVariable Long orgId, @Valid @RequestBody ParametrageClotureExerciceRequest request) {
        return clotureExerciceRepartitionService.previsualiserDepuisRequest(orgId, request);
    }
}
