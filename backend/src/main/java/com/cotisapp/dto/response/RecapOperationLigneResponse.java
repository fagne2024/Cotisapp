package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.TypeOperation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class RecapOperationLigneResponse {
    private Long operationId;
    private TypeOperation typeOperation;
    private String typeLibelle;
    private Long membreId;
    private String membreNom;
    private String codeMembre;
    private BigDecimal montant;
    private BigDecimal montantFrais;
    private BigDecimal montantTotal;
    private LocalDate dateOperation;
    private String observation;
    private boolean annulee;
    private boolean annulation;
}
