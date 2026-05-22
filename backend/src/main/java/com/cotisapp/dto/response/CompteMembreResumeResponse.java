package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CompteMembreResumeResponse {
    private Long membreId;
    private String nomComplet;
    private String codeMembre;
    private String posteLabel;
    private String initials;
    private String avatarColor;
    private BigDecimal totalSoldes;
    private BigDecimal epargne;
    private BigDecimal solidarite;
    private BigDecimal depense;
    private BigDecimal penalite;
    private BigDecimal amende;
}
