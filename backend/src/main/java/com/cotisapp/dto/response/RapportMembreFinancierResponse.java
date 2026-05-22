package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RapportMembreFinancierResponse {
    private String nom;
    private String code;
    private String initials;
    private String avColor;
    private String posteHtml;
    private String epargne;
    private String solidarite;
    private String penalite;
    private String amende;
    private String emprunt;
    private String situation;
    private String situationClass;
    private BigDecimal empruntRestant;
}
