package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class MembreRepartitionClotureResponse {
    private Long membreId;
    private String codeMembre;
    private String nomComplet;
    private int nombreParts;
    private BigDecimal pourcentageRepartition;
    private boolean excluDuPartage;
    private String motifExclusion;
    private BigDecimal montantCotisationsExercice;
    private BigDecimal montantPart;
    private BigDecimal montantInterets;
    private BigDecimal montantPenalites;
    private BigDecimal montantAmendes;
    private Map<String, BigDecimal> montantsParPoste;
}
