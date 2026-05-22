package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PenaliteAmendeStatsMoisResponse {
    private String moisLabel;
    private int penalites;
    private int amendes;
    private BigDecimal totalEncaisse;
}
