package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.TypeModeCalcul;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RetenueClotureResponse {
    private String libelle;
    private TypeModeCalcul typeMode;
    private BigDecimal valeur;
    private int ordre;
    private BigDecimal montantCalcule;
}
