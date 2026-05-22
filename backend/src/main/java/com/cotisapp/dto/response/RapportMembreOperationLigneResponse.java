package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class RapportMembreOperationLigneResponse {
    private Long id;
    private LocalDate dateOperation;
    private String dateLabel;
    private String typeOperation;
    private String libelle;
    private BigDecimal montant;
    private String montantLabel;
    private String sens;
}
