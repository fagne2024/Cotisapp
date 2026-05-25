package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** Détail caisse / solidarité pour les indicateurs du relevé organisation. */
@Data
@Builder
public class FluxCaisseSolidariteResponse {
    private BigDecimal soldeCaisse;
    private BigDecimal soldeSolidarite;
    private BigDecimal entreesCaisseMois;
    private BigDecimal entreesSolidariteMois;
    private BigDecimal sortiesCaisseMois;
    private BigDecimal sortiesSolidariteMois;
}
