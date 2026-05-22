package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RapportDepenseResponse {
    private String categorie;
    private String categorieId;
    private String beneficiaire;
    private String description;
    private BigDecimal montant;
    private String dateLabel;
    private String saisiPar;
}
