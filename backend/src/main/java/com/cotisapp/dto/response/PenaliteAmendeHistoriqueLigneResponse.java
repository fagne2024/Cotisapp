package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PenaliteAmendeHistoriqueLigneResponse {
    private Long operationId;
    private Long membreId;
    private String membreNom;
    private String codeMembre;
    /** pen ou am */
    private String type;
    private String motif;
    private BigDecimal montant;
    private LocalDate dateOperation;
    private String dateLabel;
    private boolean annulee;
}
