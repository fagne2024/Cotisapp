package com.cotisapp.controller;

import com.cotisapp.dto.request.ApprouverDemandeOperationRequest;
import com.cotisapp.dto.request.RefuserDemandeOperationRequest;
import com.cotisapp.dto.response.DemandeOperationMembreResponse;
import com.cotisapp.service.DemandeOperationMembreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organisations/{orgId}/demandes-operations")
@RequiredArgsConstructor
public class DemandeOperationMembreController {

    private final DemandeOperationMembreService demandeOperationMembreService;

    @PostMapping("/{demandeId}/approuver")
    @PreAuthorize("""
        hasRole('SUPERADMIN') or
        (hasRole('ADMIN_GIE') and @orgSecurityService.belongsTo(#orgId))
        """)
    public DemandeOperationMembreResponse approuver(
            @PathVariable Long orgId,
            @PathVariable Long demandeId,
            @RequestBody(required = false) @Valid ApprouverDemandeOperationRequest body) {
        return demandeOperationMembreService.approuver(orgId, demandeId, body);
    }

    @PostMapping("/{demandeId}/refuser")
    @PreAuthorize("""
        hasRole('SUPERADMIN') or
        (hasRole('ADMIN_GIE') and @orgSecurityService.belongsTo(#orgId))
        """)
    public DemandeOperationMembreResponse refuser(
            @PathVariable Long orgId,
            @PathVariable Long demandeId,
            @RequestBody(required = false) RefuserDemandeOperationRequest body) {
        return demandeOperationMembreService.refuser(orgId, demandeId, body);
    }
}
