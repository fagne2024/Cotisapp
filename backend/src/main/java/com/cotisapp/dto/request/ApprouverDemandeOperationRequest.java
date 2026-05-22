package com.cotisapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApprouverDemandeOperationRequest {
    /**
     * Amende optionnelle appliquée par le validateur à l'approbation (cotisation mobile money).
     * Absent ou ≤ 0 : aucune amende.
     */
    @DecimalMin(value = "0.01", message = "Le montant de l'amende doit être strictement positif")
    private BigDecimal montantAmende;
}
