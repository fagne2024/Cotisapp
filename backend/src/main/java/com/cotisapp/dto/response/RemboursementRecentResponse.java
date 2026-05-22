package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RemboursementRecentResponse {
    private Long operationId;
    private String membreNom;
    private String typeEmprunt;
    private String typeLibelle;
    private BigDecimal montantTotal;
    private String dateLabel;
    private String meta;
    private String iconeClass;
}
