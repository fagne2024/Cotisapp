package com.cotisapp.controller;

import com.cotisapp.domain.entity.Operation;
import com.cotisapp.dto.request.RembourserRequest;
import com.cotisapp.dto.response.OperationResponse;
import com.cotisapp.service.OperationMapperService;
import com.cotisapp.service.RembourserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organisations/{orgId}/emprunts/{empId}")
@RequiredArgsConstructor
public class EmpruntController {

    private final RembourserService rembourserService;
    private final OperationMapperService operationMapperService;

    @PostMapping("/rembourser")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public OperationResponse rembourser(
            @PathVariable Long orgId,
            @PathVariable Long empId,
            @Valid @RequestBody RembourserRequest request) {
        Operation op = rembourserService.rembourser(orgId, empId, request);
        return operationMapperService.toResponse(op);
    }

    @PostMapping("/rembourser/etale")
    @PreAuthorize("@orgSecurityService.peutGestionOrg(#orgId)")
    public OperationResponse rembourserEtale(
            @PathVariable Long orgId, @PathVariable Long empId,
            @Valid @RequestBody RembourserRequest request) {
        return operationMapperService.toResponse(rembourserService.rembourser(orgId, empId, request));
    }

    @PostMapping("/rembourser/solidarite")
    @PreAuthorize("@orgSecurityService.peutGestionOrg(#orgId)")
    public OperationResponse rembourserSolidarite(
            @PathVariable Long orgId, @PathVariable Long empId,
            @Valid @RequestBody RembourserRequest request) {
        return operationMapperService.toResponse(rembourserService.rembourser(orgId, empId, request));
    }

    @PostMapping("/rembourser/caisse")
    @PreAuthorize("@orgSecurityService.peutGestionOrg(#orgId)")
    public OperationResponse rembourserCaisse(
            @PathVariable Long orgId, @PathVariable Long empId,
            @Valid @RequestBody RembourserRequest request) {
        return operationMapperService.toResponse(rembourserService.rembourser(orgId, empId, request));
    }
}
