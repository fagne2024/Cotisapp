package com.cotisapp.controller;

import com.cotisapp.dto.request.Reinitialiser2faMaintenanceRequest;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.service.UtilisateurSecuriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Récupération hors session (perte d'accès Google Authenticator).
 * Protégé par une clé de maintenance — à changer en production.
 */
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final UtilisateurSecuriteService utilisateurSecuriteService;

    @Value("${cotisapp.maintenance.recovery-key}")
    private String recoveryKey;

    @PostMapping("/reinitialiser-2fa")
    public Map<String, String> reinitialiser2fa(@Valid @RequestBody Reinitialiser2faMaintenanceRequest request) {
        if (recoveryKey == null
                || recoveryKey.isBlank()
                || !recoveryKey.equals(request.getCle())) {
            throw new BusinessException("Clé de maintenance invalide");
        }
        String message = utilisateurSecuriteService.reinitialiserTwoFactorParEmail(request.getEmail());
        return Map.of("message", message);
    }
}
