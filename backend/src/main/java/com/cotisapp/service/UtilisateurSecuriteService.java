package com.cotisapp.service;

import com.cotisapp.config.DataInitializer;
import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UtilisateurSecuriteService {

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurRoleRepository utilisateurRoleRepository;
    private final JournalService journalService;

    @Transactional
    public String reinitialiserTwoFactorParEmail(String email) {
        String emailNorm = email.trim().toLowerCase(Locale.ROOT);
        Utilisateur user = utilisateurRepository
                .findByEmail(emailNorm)
                .orElseThrow(() -> new BusinessException("Aucun compte avec l'e-mail « " + emailNorm + " »"));

        boolean superadmin = utilisateurRoleRepository.findByUtilisateurId(user.getId()).stream()
                .anyMatch(r -> r.getRole() == Role.SUPERADMIN);
        if (!superadmin && !estCompteSysteme(emailNorm)) {
            throw new BusinessException(
                    "Cette récupération ne s'applique qu'aux comptes superadmin ("
                            + DataInitializer.EMAIL_SUPERADMIN
                            + ", "
                            + DataInitializer.EMAIL_ADMIN
                            + ")");
        }

        user.setTotpSecret(null);
        user.setTotpEnabled(false);
        utilisateurRepository.save(user);

        String cible = JournalModificationFormatter.cibleUtilisateur(
                user.getPrenom(), user.getNom(), user.getEmail(), user.getId());
        journalService.enregistrer(
                null,
                "2FA_DESACTIVE",
                "Réinitialisation Google Authenticator (maintenance) pour " + cible);

        return "Double authentification réinitialisée pour " + cible
                + ". Reconnectez-vous avec le mot de passe, puis reconfigurez Google Authenticator.";
    }

    private static boolean estCompteSysteme(String email) {
        return DataInitializer.EMAIL_SUPERADMIN.equalsIgnoreCase(email)
                || DataInitializer.EMAIL_ADMIN.equalsIgnoreCase(email);
    }
}
