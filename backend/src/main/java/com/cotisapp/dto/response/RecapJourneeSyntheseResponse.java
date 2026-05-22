package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RecapJourneeSyntheseResponse {
    private int nbOperationsActives;
    private int nbAnnulations;
    private int nbCotisations;
    private BigDecimal montantCotisations;
    private int nbEmprunts;
    private BigDecimal montantEmprunts;
    private int nbRemboursements;
    private BigDecimal montantRemboursements;
    private int nbMembresConcernes;
    private BigDecimal entreesCaisse;
    private BigDecimal sortiesCaisse;
}
