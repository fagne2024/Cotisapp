package com.cotisapp.service;

import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.dto.request.TwoFactorConfirmRequest;
import com.cotisapp.dto.request.TwoFactorDisableRequest;
import com.cotisapp.dto.response.TwoFactorSetupResponse;
import com.cotisapp.dto.response.TwoFactorStatusResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.security.OrganisationContext;
import com.cotisapp.security.TotpPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private final UtilisateurRepository utilisateurRepository;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;
    private final JournalService journalService;

    @Transactional(readOnly = true)
    public TwoFactorStatusResponse status() {
        Utilisateur u = utilisateurCourant();
        boolean pending = u.getTotpSecret() != null && !Boolean.TRUE.equals(u.getTotpEnabled());
        return TwoFactorStatusResponse.builder()
                .enabled(Boolean.TRUE.equals(u.getTotpEnabled()))
                .pendingSetup(pending)
                .build();
    }

    @Transactional
    public TwoFactorSetupResponse demarrerConfiguration() {
        Utilisateur u = utilisateurCourant();
        if (Boolean.TRUE.equals(u.getTotpEnabled())) {
            throw new BusinessException("La double authentification est déjà activée");
        }
        String secret = totpService.generateSecret();
        u.setTotpSecret(totpService.encryptSecret(secret));
        u.setTotpEnabled(false);
        utilisateurRepository.save(u);
        journalService.enregistrer(OrganisationContext.getOrganisationId(), "2FA_SETUP_DEMARRE", "Configuration Google Authenticator démarrée");
        return totpService.buildSetupResponse(u, secret);
    }

    @Transactional
    public TwoFactorStatusResponse confirmerConfiguration(TwoFactorConfirmRequest request) {
        Utilisateur u = utilisateurCourant();
        if (Boolean.TRUE.equals(u.getTotpEnabled())) {
            throw new BusinessException("La double authentification est déjà activée");
        }
        if (u.getTotpSecret() == null) {
            throw new BusinessException("Aucune configuration en cours. Relancez l'activation.");
        }
        String plain = totpService.decryptSecret(u.getTotpSecret());
        if (!totpService.verifyPlainSecret(plain, request.getCode())) {
            throw new BusinessException("Code incorrect. Vérifiez l'heure de votre téléphone et réessayez.");
        }
        u.setTotpEnabled(true);
        utilisateurRepository.save(u);
        journalService.enregistrer(OrganisationContext.getOrganisationId(), "2FA_ACTIVE", "Double authentification activée");
        return status();
    }

    @Transactional
    public TwoFactorStatusResponse desactiver(TwoFactorDisableRequest request) {
        Utilisateur u = utilisateurCourant();
        Role role = OrganisationContext.getRole();
        if (role != null && TotpPolicy.isAdminRole(role)) {
            throw new BusinessException(
                    "La double authentification est obligatoire pour les comptes administrateur et ne peut pas être désactivée");
        }
        if (!Boolean.TRUE.equals(u.getTotpEnabled())) {
            throw new BusinessException("La double authentification n'est pas activée");
        }
        if (!passwordEncoder.matches(request.getMotDePasse(), u.getMotDePasse())) {
            throw new BusinessException("Mot de passe incorrect");
        }
        if (!totpService.verifyUtilisateur(u, request.getCode())) {
            throw new BusinessException("Code d'authentification incorrect");
        }
        u.setTotpSecret(null);
        u.setTotpEnabled(false);
        utilisateurRepository.save(u);
        journalService.enregistrer(OrganisationContext.getOrganisationId(), "2FA_DESACTIVE", "Double authentification désactivée");
        return status();
    }

    @Transactional
    public void annulerConfiguration() {
        Utilisateur u = utilisateurCourant();
        if (Boolean.TRUE.equals(u.getTotpEnabled())) {
            throw new BusinessException("La double authentification est déjà active");
        }
        u.setTotpSecret(null);
        utilisateurRepository.save(u);
    }

    private Utilisateur utilisateurCourant() {
        Long userId = OrganisationContext.getUserId();
        if (userId == null) {
            throw new BusinessException("Utilisateur non authentifié");
        }
        return utilisateurRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Compte introuvable"));
    }
}
