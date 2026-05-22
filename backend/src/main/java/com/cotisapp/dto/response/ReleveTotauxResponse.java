package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ReleveTotauxResponse {
    private BigDecimal entrees;
    private BigDecimal sorties;
    private BigDecimal variationNette;
    private int nbOperations;
    private int nbAnnulees;
}
