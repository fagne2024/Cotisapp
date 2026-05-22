package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class EmpruntHistoriqueLigneResponse {
    private String ligneId;
    private Long operationId;
    private Long empruntId;
    /** ETALE, CAISSE, SOLIDARITE */
    private String typeEmprunt;
    private String typeLibelle;
    private Long membreId;
    private String membreNom;
    private String codeMembre;
    private BigDecimal montantCapital;
    private BigDecimal montantFrais;
    private BigDecimal montantTotal;
    private Integer nbEcheances;
    private LocalDate dateOperation;
    private String dateLabel;
    private String observation;
    private boolean annulee;
    private boolean annulable;
}
