package com.cotisapp.controller;

import com.cotisapp.domain.entity.Operation;
import com.cotisapp.dto.request.CotisationHebdoRequest;
import com.cotisapp.dto.request.CotisationMoisRequest;
import com.cotisapp.dto.response.MouvementPreviewResponse;
import com.cotisapp.dto.response.OperationResponse;
import com.cotisapp.service.MoteurOperationService;
import com.cotisapp.service.OperationMapperService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations/{orgId}")
@RequiredArgsConstructor
public class OperationController {

    private final MoteurOperationService moteurOperationService;
    private final OperationMapperService operationMapperService;

    @PostMapping("/operations/cotisation-hebdo")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public OperationResponse cotisationHebdo(
            @PathVariable Long orgId,
            @Valid @RequestBody CotisationHebdoRequest request) {
        Operation op = moteurOperationService.cotisationHebdo(orgId, request);
        return operationMapperService.toResponse(op);
    }

    @PostMapping("/operations/cotisation-hebdo/preview")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<MouvementPreviewResponse> previewCotisationHebdo(
            @PathVariable Long orgId,
            @Valid @RequestBody CotisationHebdoRequest request) {
        return operationMapperService.previewCotisationHebdo(orgId, request);
    }

    @PostMapping("/operations/cotisation-mois")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public OperationResponse cotisationMois(
            @PathVariable Long orgId,
            @Valid @RequestBody CotisationMoisRequest request) {
        Operation op = moteurOperationService.cotisationMois(orgId, request);
        return operationMapperService.toResponse(op);
    }

    @PostMapping("/operations/cotisation-mois/preview")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<MouvementPreviewResponse> previewCotisationMois(
            @PathVariable Long orgId,
            @Valid @RequestBody CotisationMoisRequest request) {
        return operationMapperService.previewCotisationMois(orgId, request);
    }
}
