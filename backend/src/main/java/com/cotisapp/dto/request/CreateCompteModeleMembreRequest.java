package com.cotisapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCompteModeleMembreRequest {

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Code en majuscules, chiffres et underscore uniquement")
    private String code;

    @NotBlank
    @Size(max = 255)
    private String libelle;
}
