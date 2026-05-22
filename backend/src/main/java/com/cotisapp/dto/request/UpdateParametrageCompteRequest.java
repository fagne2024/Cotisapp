package com.cotisapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateParametrageCompteRequest {
    @NotBlank
    @Size(max = 255)
    private String libelle;

    private Boolean actif;
}
