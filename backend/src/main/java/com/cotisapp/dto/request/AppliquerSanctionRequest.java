package com.cotisapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AppliquerSanctionRequest {

    @NotNull
    private Long membreId;

    /** PENALITE ou AMENDE */
    @NotBlank
    private String type;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal montant;

    @NotNull
    private LocalDate dateOperation;

    @NotBlank
    private String motif;

    private String observation;
}
