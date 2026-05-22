package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.TypeCompte;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RecapCompteResponse {
    private TypeCompte typeCompte;
    private String libelle;
    private BigDecimal variationJour;
    private BigDecimal soldeFinJournee;
    private BigDecimal soldeActuel;
}
