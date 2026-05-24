package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private Long userId;
    private String email;
    private String nomComplet;
    private Role role;
    private Long organisationId;
    private String organisationNom;
    private Long membreId;
    /** Compte de gestion bureau (SG, trésorier…) sans espace membre simple. */
    private boolean compteBureau;
    private boolean mustChangePassword;
    private boolean requiresTwoFactor;
    private String twoFactorToken;
    private boolean mustSetupTwoFactor;
    private String refreshToken;
}
