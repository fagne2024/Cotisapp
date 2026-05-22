package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PenaliteAmendePanneauResponse {
    private BigDecimal soldeCaisse;
    private PenaliteAmendeStatsMoisResponse statsMois;
    private List<PenaliteAmendeHistoriqueLigneResponse> historique;
    private List<PenaliteAmendeTopMembreResponse> topPenalises;
}
