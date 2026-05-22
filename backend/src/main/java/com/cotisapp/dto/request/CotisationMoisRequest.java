package com.cotisapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CotisationMoisRequest {
    @NotNull
    private Long membreId;
    @NotBlank
    private String moisAnnee;
    @NotNull @DecimalMin("0.01")
    private BigDecimal montant;
    @NotNull
    private LocalDate dateOperation;
    private String observation;
    /** Amende optionnelle, bornée par la règle de cotisation. */
    private BigDecimal montantAmende;
    private String modePaiement;
    private String referencePaiement;
}
