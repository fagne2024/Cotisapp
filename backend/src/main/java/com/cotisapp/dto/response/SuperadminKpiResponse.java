package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SuperadminKpiResponse {
    private long organisationsActives;
    private long totalMembres;
    private BigDecimal caisseTotale;
    private long empruntsActifs;
    private long empruntsEnRetard;
    private BigDecimal solidariteTotale;
}
