package com.cotisapp.controller;

import com.cotisapp.domain.enums.TypeEvenementJournal;
import com.cotisapp.dto.request.EnregistrerEvenementJournalRequest;
import com.cotisapp.dto.response.JournalUtilisateurResponse;
import com.cotisapp.service.JournalUtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/organisations/{orgId}/journal-utilisateur")
@RequiredArgsConstructor
public class JournalUtilisateurController {

    private final JournalUtilisateurService journalUtilisateurService;

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.peutActionOrg(#orgId, 'ADMIN_JOURNAL')")
    public Page<JournalUtilisateurResponse> lister(
            @PathVariable Long orgId,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) TypeEvenementJournal type,
            @RequestParam(required = false) Boolean succes,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return journalUtilisateurService.lister(orgId, utilisateurId, type, succes, search, page, size);
    }

    @GetMapping("/count")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.peutActionOrg(#orgId, 'ADMIN_JOURNAL')")
    public Map<String, Long> compter(@PathVariable Long orgId) {
        return Map.of("total", journalUtilisateurService.compter(orgId));
    }

    @PostMapping("/evenement")
    @PreAuthorize("isAuthenticated()")
    public void enregistrerEvenement(
            @PathVariable Long orgId,
            @Valid @RequestBody EnregistrerEvenementJournalRequest request,
            HttpServletRequest httpRequest) {
        journalUtilisateurService.enregistrerEvenementClient(orgId, request, httpRequest);
    }
}
