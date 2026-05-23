package com.cotisapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangeMotDePasseInitialRequest {

    @NotBlank
    @Size(min = 8, max = 128)
    private String nouveauMotDePasse;

    @NotBlank
    @Size(min = 8, max = 128)
    private String confirmationMotDePasse;
}
