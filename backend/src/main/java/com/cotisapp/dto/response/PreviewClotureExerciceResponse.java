package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PreviewClotureExerciceResponse {
    private Long exerciceId;
    private int exerciceNumero;
    private BigDecimal poolInterets;
    private BigDecimal poolPenalites;
    private BigDecimal poolAmendes;
    private String modeRepartition;
    private String modeAgregationPostes;
    private String modeCalculProrata;
    private boolean exclureMembresPretEnCours;
    private List<PostePartageClotureResponse> postes;
    private BigDecimal poolBrut;
    private BigDecimal fraisCloture;
    private List<RetenueClotureResponse> retenues;
    private BigDecimal totalRetenues;
    private BigDecimal netADistribuer;
    private int totalParts;
    private List<MembreRepartitionClotureResponse> membres;
}
