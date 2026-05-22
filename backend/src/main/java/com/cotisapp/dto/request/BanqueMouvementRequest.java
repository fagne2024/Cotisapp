package com.cotisapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BanqueMouvementRequest {

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal montant;

    /** vers = caisse → banque, ret = banque → caisse */
    @NotBlank
    private String type;

    @NotNull
    private LocalDate dateOperation;

    private String reference;

    private String banqueAgence;

    private String description;

    private String contreSigne;
}
