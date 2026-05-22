package com.cotisapp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminGieCreationRequest {

    @NotBlank
    private String prenom;

    @NotBlank
    private String nom;

    @NotBlank
    @Email
    private String email;

    /** Si vide, mot de passe par défaut appliqué côté service. */
    private String motDePasse;
}
