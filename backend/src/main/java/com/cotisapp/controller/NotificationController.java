package com.cotisapp.controller;

import com.cotisapp.dto.response.NotificationCompteurResponse;
import com.cotisapp.dto.response.NotificationResponse;
import com.cotisapp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations/{orgId}/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    private static final String ACCES_ORG = """
        hasRole('SUPERADMIN') or
        (hasRole('ADMIN_GIE') and @orgSecurityService.belongsTo(#orgId)) or
        (hasRole('MEMBRE') and @orgSecurityService.isMemberOf(#orgId))
        """;

    @GetMapping
    @PreAuthorize(ACCES_ORG)
    public List<NotificationResponse> lister(@PathVariable Long orgId) {
        return notificationService.lister(orgId);
    }

    @GetMapping("/compteur")
    @PreAuthorize(ACCES_ORG)
    public NotificationCompteurResponse compteur(@PathVariable Long orgId) {
        return notificationService.compteur(orgId);
    }

    @PutMapping("/lire-tout")
    @PreAuthorize(ACCES_ORG)
    public void marquerToutLu(@PathVariable Long orgId) {
        notificationService.marquerToutLu(orgId);
    }

    @PutMapping("/{cle}/lire")
    @PreAuthorize(ACCES_ORG)
    public void marquerLu(@PathVariable Long orgId, @PathVariable String cle) {
        notificationService.marquerLu(orgId, cle);
    }

    @PutMapping("/{cle}/non-lire")
    @PreAuthorize(ACCES_ORG)
    public void marquerNonLu(@PathVariable Long orgId, @PathVariable String cle) {
        notificationService.marquerNonLu(orgId, cle);
    }

    @PutMapping("/{cle}/masquer")
    @PreAuthorize(ACCES_ORG)
    public void masquer(@PathVariable Long orgId, @PathVariable String cle) {
        notificationService.masquer(orgId, cle);
    }
}
