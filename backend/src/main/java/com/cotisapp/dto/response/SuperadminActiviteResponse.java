package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SuperadminActiviteResponse {
    private String icone;
    private String fondCouleur;
    private String libelle;
    private String meta;
    private BigDecimal montant;
    private boolean credit;
}
