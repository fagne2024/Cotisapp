package com.cotisapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DepenseRequest {

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal montant;

    /** caisse ou banque */
    @NotBlank
    private String compteDebite;

    private String beneficiaire;

    @NotNull
    private LocalDate dateDepense;

    private String description;

    @NotBlank
    private String categorieId;
}
