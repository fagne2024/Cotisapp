package com.cotisapp.controller;

import com.cotisapp.dto.request.CreerJourneeReunionRequest;
import com.cotisapp.dto.response.JourneeReunionResponse;
import com.cotisapp.dto.response.RecapJourneeResponse;
import com.cotisapp.service.RecapJourneeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/organisations/{orgId}/recap-journees")
@RequiredArgsConstructor
public class RecapJourneeController {

    private final RecapJourneeService recapJourneeService;

    @GetMapping
    public List<JourneeReunionResponse> lister(
            @PathVariable Long orgId,
            @RequestParam(required = false) Long exerciceId) {
        return recapJourneeService.lister(orgId, exerciceId);
    }

    @PostMapping
    public JourneeReunionResponse creer(
            @PathVariable Long orgId,
            @RequestBody CreerJourneeReunionRequest request) {
        return recapJourneeService.creer(orgId, request);
    }

    @PostMapping("/{journeeId}/cloturer")
    public JourneeReunionResponse cloturer(@PathVariable Long orgId, @PathVariable Long journeeId) {
        return recapJourneeService.cloturer(orgId, journeeId);
    }

    @PostMapping("/{journeeId}/reouvrir")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public JourneeReunionResponse reouvrir(@PathVariable Long orgId, @PathVariable Long journeeId) {
        return recapJourneeService.reouvrir(orgId, journeeId);
    }

    @GetMapping("/par-date")
    public RecapJourneeResponse recapParDate(
            @PathVariable Long orgId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return recapJourneeService.recapParDate(orgId, date);
    }

    @GetMapping("/{journeeId}")
    public RecapJourneeResponse recapParId(@PathVariable Long orgId, @PathVariable Long journeeId) {
        return recapJourneeService.recapParId(orgId, journeeId);
    }
}
