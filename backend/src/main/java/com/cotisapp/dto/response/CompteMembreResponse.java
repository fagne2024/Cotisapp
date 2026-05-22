package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.TypeCompte;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CompteMembreResponse {
    private Long id;
    private TypeCompte typeCompte;
    private String libelle;
    private BigDecimal solde;
    private Long modeleCompteId;
    private String modeleCode;
}
