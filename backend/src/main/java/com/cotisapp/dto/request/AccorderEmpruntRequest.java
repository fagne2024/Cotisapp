package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.TypeEmprunt;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AccorderEmpruntRequest {
    @NotNull
    private Long membreId;

    @NotNull
    private TypeEmprunt typeEmprunt;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal montant;

    private Integer nbEcheances;

    @NotNull
    private LocalDate dateOctroi;

    private String observation;
}
