package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MembreSoldesResponse {
    private Long membreId;
    private BigDecimal epargneHebdo;
    private BigDecimal epargneMois;
    private BigDecimal solidarite;
    private BigDecimal penalite;
    private BigDecimal amende;
    private BigDecimal depense;
}
