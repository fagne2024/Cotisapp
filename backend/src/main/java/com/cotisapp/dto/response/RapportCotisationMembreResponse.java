package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RapportCotisationMembreResponse {
    private String nom;
    private String code;
    private String initials;
    private String avColor;
    private String posteLabel;
    private String posteBadgeClass;
    private String hebdo;
    private String mois;
    private String solidarite;
    private String total;
    private String statut;
    private String statutLabel;
    private BigDecimal totalMontant;
}
