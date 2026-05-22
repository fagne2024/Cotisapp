package com.cotisapp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfilRequest {
    @NotBlank
    @Size(max = 80)
    private String prenom;

    @NotBlank
    @Size(max = 80)
    private String nom;

    @NotBlank
    @Email
    private String email;

    @Size(max = 32)
    private String telephone;

    @Size(max = 32)
    private String telephoneSecondaire;

    @Size(max = 255)
    private String adresse;
}
