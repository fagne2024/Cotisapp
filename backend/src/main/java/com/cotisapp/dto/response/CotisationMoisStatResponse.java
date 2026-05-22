package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CotisationMoisStatResponse {
    /** Mois calendaire 1–12 */
    private int mois;
    private BigDecimal montantCotisations;
    private BigDecimal objectif;
}
