package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PenaliteAmendeTopMembreResponse {
    private Long membreId;
    private String nom;
    private String codeMembre;
    private String detail;
    private BigDecimal total;
}
