package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.TypeModeCalcul;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RetenueClotureRequest {
    @NotBlank
    private String libelle;
    @NotNull
    private TypeModeCalcul typeMode;
    @NotNull
    private BigDecimal valeur;
    private int ordre;
}
