package com.cotisapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CotisationHebdoRequest {
    @NotNull
    private Long membreId;
    @NotBlank
    private String semaineKey;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal montant;
    @NotNull
    private LocalDate dateOperation;
    private String observation;
    /** Amende optionnelle, bornée par la règle de cotisation. */
    private BigDecimal montantAmende;
    /** ESPECES, WAVE, ORANGE_MONEY (défaut ESPECES). */
    private String modePaiement;
    /** N° transaction Wave / Orange Money (optionnel). */
    private String referencePaiement;
}
