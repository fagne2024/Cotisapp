package com.cotisapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TwoFactorDisableRequest {

    @NotBlank
    private String motDePasse;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "Le code doit contenir 6 chiffres")
    private String code;
}
