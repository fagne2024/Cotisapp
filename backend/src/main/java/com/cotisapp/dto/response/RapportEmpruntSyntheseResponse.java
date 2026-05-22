package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RapportEmpruntSyntheseResponse {
    private long enCours;
    private long enRetard;
    private long soldesMois;
    private BigDecimal encoursTotal;
    private BigDecimal remboursementsMois;
    private BigDecimal fraisRestants;
}
