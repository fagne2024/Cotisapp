package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MouvementPreviewResponse {
    private String libelle;
    private String sens;
    private BigDecimal montant;
}
