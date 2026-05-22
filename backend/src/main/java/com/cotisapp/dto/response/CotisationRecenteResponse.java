package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CotisationRecenteResponse {
    private String membreNom;
    private String libelle;
    private String meta;
    private BigDecimal montant;
    private String iconeClass;
}
