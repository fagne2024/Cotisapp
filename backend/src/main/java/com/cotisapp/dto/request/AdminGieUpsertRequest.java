package com.cotisapp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminGieUpsertRequest {

    @NotBlank
    private String prenom;

    @NotBlank
    private String nom;

    @NotBlank
    @Email
    private String email;

    /** Si renseigné, remplace le mot de passe. */
    private String motDePasse;

    /** Si true avec un nouveau mot de passe, impose le changement à la prochaine connexion. */
    private Boolean forcerChangementMotDePasse;

    private Boolean compteActif = true;
}
