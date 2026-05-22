package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CotisationPanneauResponse {
    private String periodeLabel;
    private List<CotisationSuiviMembreResponse> suivi;
    private List<CotisationRecenteResponse> recentes;
    private int cotisationsAujourdhui;
    private BigDecimal montantAujourdhui;
}
