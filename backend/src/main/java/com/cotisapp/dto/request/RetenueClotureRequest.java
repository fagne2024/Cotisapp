package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.TypeModeCalcul;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RetenueClotureRequest {
    @NotBlank
    @Size(max = 255)
    private String libelle;
    @NotNull
    private TypeModeCalcul typeMode;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal valeur;
    private int ordre;
}
