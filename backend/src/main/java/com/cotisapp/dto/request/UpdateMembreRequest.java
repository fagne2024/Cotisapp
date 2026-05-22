package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.PosteMembre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateMembreRequest {

    @NotBlank
    @Size(max = 100)
    private String prenom;

    @NotBlank
    @Size(max = 100)
    private String nom;

    @Size(max = 255)
    private String email;

    @Size(max = 30)
    private String telephone;

    private LocalDate dateAdhesion;

    @Size(max = 80)
    private String pieceIdentite;

    @NotNull
    private PosteMembre poste;

    @NotNull
    private Boolean actif;
}
