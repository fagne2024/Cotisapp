package com.cotisapp.controller;

import com.cotisapp.dto.request.OuvrirExerciceRequest;
import com.cotisapp.dto.response.ExerciceResponse;
import com.cotisapp.dto.response.PreviewClotureExerciceResponse;
import com.cotisapp.service.ClotureExerciceRepartitionService;
import com.cotisapp.service.ExerciceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations/{orgId}/exercices")
@RequiredArgsConstructor
public class ExerciceController {

    private final ExerciceService exerciceService;
    private final ClotureExerciceRepartitionService clotureExerciceRepartitionService;

    @GetMapping
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<ExerciceResponse> lister(@PathVariable Long orgId) {
        return exerciceService.lister(orgId);
    }

    @GetMapping("/courant")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId) or
        (hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId))
        """)
    public ExerciceResponse courant(@PathVariable Long orgId) {
        return exerciceService.getCourant(orgId);
    }

    @GetMapping("/courant/preview-repartition")
    @PreAuthorize("@orgSecurityService.peutGestionOrg(#orgId)")
    public PreviewClotureExerciceResponse previewRepartition(@PathVariable Long orgId) {
        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        return clotureExerciceRepartitionService.previsualiser(orgId, exerciceId);
    }

    @PostMapping("/transition")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public ExerciceResponse transition(
            @PathVariable Long orgId,
            @RequestBody(required = false) OuvrirExerciceRequest request) {
        return exerciceService.cloturerEtOuvrirSuivant(orgId, request != null ? request : new OuvrirExerciceRequest());
    }

    @PostMapping("/{exerciceId}/reouvrir")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ExerciceResponse reouvrir(@PathVariable Long orgId, @PathVariable Long exerciceId) {
        return exerciceService.reouvrir(orgId, exerciceId);
    }
}
