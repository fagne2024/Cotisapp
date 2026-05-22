package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class RapportMembreResponse {
    private Long membreId;
    private String nom;
    private String code;
    private String initials;
    private String avColor;
    private String posteLabel;
    private String posteBadgeClass;
    private String periode;
    private String periodeLabel;
    private List<RapportPeriodeOption> periodesDisponibles;
    private List<RapportHeroStatResponse> heroStats;
    private String hebdo;
    private String mois;
    private String solidarite;
    private String totalCotisationsLabel;
    private BigDecimal totalCotisations;
    private String statutCotisation;
    private String statutCotisationLabel;
    private List<RapportBarChartItemResponse> cotisationsParSemaine;
    private List<RapportEmpruntCardResponse> emprunts;
    private List<RapportMembreOperationLigneResponse> operations;
    private MembreSoldeMembreResponse soldeMembre;
    private List<RapportMembreCompteResponse> comptes;
}
