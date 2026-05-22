package com.cotisapp.controller;

import com.cotisapp.dto.response.RapportResponse;
import com.cotisapp.service.RapportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organisations/{orgId}/rapports")
@RequiredArgsConstructor
public class RapportController {

    private final RapportService rapportService;

    @GetMapping
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public RapportResponse generer(
            @PathVariable Long orgId,
            @RequestParam(required = false) String periode) {
        return rapportService.generer(orgId, periode);
    }
}
