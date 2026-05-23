package com.cotisapp.controller;

import com.cotisapp.dto.request.ReinitialiserAdminMdpRequest;
import com.cotisapp.dto.response.SuperadminVueGlobaleResponse;
import com.cotisapp.dto.response.UtilisateurOrgResponse;
import com.cotisapp.service.SuperadminVueService;
import com.cotisapp.service.UtilisateurAccesService;
import com.cotisapp.service.UtilisateurSecuriteService;
import com.cotisapp.security.OrganisationContext;
import com.cotisapp.repository.UtilisateurRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
public class SuperadminController {

    private final SuperadminVueService superadminVueService;
    private final UtilisateurAccesService utilisateurAccesService;
    private final UtilisateurSecuriteService utilisateurSecuriteService;
    private final UtilisateurRepository utilisateurRepository;

    @GetMapping("/vue-globale")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public SuperadminVueGlobaleResponse vueGlobale() {
        return superadminVueService.vueGlobale();
    }

    @PutMapping("/organisations/{orgId}/admin-gie/mot-de-passe")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public UtilisateurOrgResponse reinitialiserMotDePasseAdminGie(
            @PathVariable Long orgId, @Valid @RequestBody ReinitialiserAdminMdpRequest request) {
        return utilisateurAccesService.reinitialiserMotDePasseAdminGie(orgId, request);
    }

    @PostMapping("/organisations/{orgId}/admin-gie/2fa/reinitialiser")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public UtilisateurOrgResponse reinitialiserTwoFactorAdminGie(@PathVariable Long orgId) {
        return utilisateurAccesService.reinitialiserTwoFactorAdminGie(orgId);
    }

    /** Réinitialise Google Authenticator pour un compte superadmin (dont le vôtre). */
    @PostMapping("/comptes-superadmin/2fa/reinitialiser")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public Map<String, String> reinitialiserTwoFactorCompteSuperadmin() {
        Long userId = OrganisationContext.getUserId();
        if (userId == null) {
            throw new com.cotisapp.exception.BusinessException("Utilisateur non authentifié");
        }
        String email = utilisateurRepository
                .findById(userId)
                .map(com.cotisapp.domain.entity.Utilisateur::getEmail)
                .orElseThrow(() -> new com.cotisapp.exception.BusinessException("Compte introuvable"));
        String message = utilisateurSecuriteService.reinitialiserTwoFactorParEmail(email);
        return Map.of("message", message);
    }
}
