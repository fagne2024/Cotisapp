package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUtilisateurOrgRequest {
    @NotBlank
    private String prenom;

    @NotBlank
    private String nom;

    @NotBlank
    @Email
    private String email;

    private String motDePasse;

    @NotNull
    private Role role;

    private PosteMembre poste;

    private Long membreId;

    private Long typeProfilId;

    @NotNull
    private Boolean compteActif;

    /** Si true, envoie (ou journalise) l’email avec le mot de passe initial membre. */
    private Boolean envoyerEmailActivation;
}
