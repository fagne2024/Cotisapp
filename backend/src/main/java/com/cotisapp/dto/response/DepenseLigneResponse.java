package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class DepenseLigneResponse {
    private Long id;
    private String categorieId;
    private String categorieLabel;
    private BigDecimal montant;
    private LocalDate dateOperation;
    private String beneficiaire;
    private String description;
}
