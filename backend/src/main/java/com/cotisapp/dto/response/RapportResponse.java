package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class RapportResponse {
    private String periode;
    private String periodeLabel;
    private int nbMembresActifs;
    private int nbMembresBureau;
    private List<RapportPeriodeOption> periodesDisponibles;
    private List<RapportHeroStatResponse> heroStats;
    private List<RapportBarChartItemResponse> cotisationsParSemaine;
    private RapportParticipationResponse participation;
    private BigDecimal totalCotisations;
    private List<RapportCotisationMembreResponse> cotisationsMembres;
    private List<RapportEmpruntCardResponse> emprunts;
    private RapportEmpruntSyntheseResponse empruntsSynthese;
    private List<RapportMembreFinancierResponse> membresFinancier;
    private List<RapportDepenseResponse> depenses;
    private BigDecimal totalDepenses;
}
