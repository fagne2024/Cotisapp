package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardResponse {
    private BigDecimal soldeCaisse;
    private BigDecimal soldeSolidarite;
    private BigDecimal soldeBanque;
    private long nbMembresActifs;
    private long nbMembresBureau;
    private long nbMembresSimples;
    private long nbEmpruntsEnCours;
    private long nbEmpruntsEnRetard;
    private List<OperationResponse> operationsRecentes;
    private List<MembreResponse> bureau;
    /** Cotisations hebdo + mensuelles par mois calendaire (année courante, exercice en cours). */
    private List<CotisationMoisStatResponse> evolutionCotisations;
    private int evolutionAnnee;
}
