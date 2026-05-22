package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class MouvementCaisseLigneResponse {
    private Long id;
    private LocalDate dateOperation;
    private String sens;
    private BigDecimal montant;
    private BigDecimal soldeCaisseApres;
    private String typeOperation;
    private String libelle;
}
