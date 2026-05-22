package com.cotisapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    /** Email (admin / superadmin) ou numéro de téléphone (membre). */
    @NotBlank
    private String identifiant;

    @NotBlank
    private String motDePasse;

    /** Obligatoire si plusieurs comptes membres partagent le même téléphone (même organisation). */
    private Long organisationId;

    /** Identifie la fiche membre lorsque plusieurs comptes partagent le même téléphone. */
    private Long membreId;

    /** ADMIN_GIE ou SUPERADMIN — si le même email possède les deux rôles. */
    private String roleSouhaite;
}
