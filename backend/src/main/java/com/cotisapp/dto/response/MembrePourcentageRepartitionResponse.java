package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MembrePourcentageRepartitionResponse {
    private Long membreId;
    private String codeMembre;
    private String nomComplet;
    private BigDecimal pourcentage;
}
