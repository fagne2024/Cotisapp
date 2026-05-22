package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CotisationOrgChartResponse {
    private String code;
    private String nom;
    private BigDecimal montant;
}
