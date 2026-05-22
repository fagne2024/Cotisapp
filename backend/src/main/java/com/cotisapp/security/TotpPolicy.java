package com.cotisapp.security;

import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.enums.Role;

public final class TotpPolicy {

    private TotpPolicy() {}

    public static boolean isAdminRole(Role role) {
        return role == Role.SUPERADMIN || role == Role.ADMIN_GIE;
    }

    public static boolean twoFactorEnabled(Utilisateur utilisateur) {
        return Boolean.TRUE.equals(utilisateur.getTotpEnabled()) && utilisateur.getTotpSecret() != null;
    }

    /** 2FA obligatoire pour les administrateurs qui ne l'ont pas encore activée. */
    public static boolean mustSetupTwoFactor(Role role, Utilisateur utilisateur) {
        return isAdminRole(role) && !twoFactorEnabled(utilisateur);
    }
}
