package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class RemboursementPanneauResponse {
    private BigDecimal soldeCaisse;
    private BigDecimal soldeSolidarite;
    private List<RemboursementRecentResponse> recentes;
}
