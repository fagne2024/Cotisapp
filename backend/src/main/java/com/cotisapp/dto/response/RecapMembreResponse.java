package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RecapMembreResponse {
    private Long membreId;
    private String codeMembre;
    private String membreNom;
    private int nbOperations;
    private BigDecimal montantCotisations;
    private BigDecimal montantEmprunts;
    private BigDecimal montantRemboursements;
    private BigDecimal variationNetComptes;
}
