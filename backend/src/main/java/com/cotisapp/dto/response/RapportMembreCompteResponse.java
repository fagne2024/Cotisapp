package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RapportMembreCompteResponse {
    private String typeCompte;
    private String libelle;
    private BigDecimal solde;
    private String soldeLabel;
}
