package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class RemboursementHistoriqueLigneResponse {
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
    private BigDecimal montantPenalite;
    private BigDecimal montantTotal;
    private LocalDate dateOperation;
    private String dateLabel;
    private String observation;
    private String modePaiementLibelle;
    private String referencePaiement;
    private boolean annulee;
    private boolean annulable;
}
