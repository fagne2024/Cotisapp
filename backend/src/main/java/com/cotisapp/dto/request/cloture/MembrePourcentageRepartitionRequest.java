package com.cotisapp.dto.request.cloture;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MembrePourcentageRepartitionRequest {
    @NotNull
    private Long membreId;
    @NotNull
    private BigDecimal pourcentage;
}
