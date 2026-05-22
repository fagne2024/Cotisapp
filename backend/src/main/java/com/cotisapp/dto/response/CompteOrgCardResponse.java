package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.TypeCompte;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CompteOrgCardResponse {
    private Long compteId;
    private TypeCompte typeCompte;
    private String libelle;
    private String sousTitre;
    private BigDecimal solde;
    private BigDecimal variationJour;
    private String icone;
}
