package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class MouvementBanqueLigneResponse {
    private Long id;
    private LocalDate dateOperation;
    private String type;
    private BigDecimal montant;
    private BigDecimal soldeBanqueApres;
    private String reference;
    private String description;
    private Long releveId;
    private String releveNomFichier;
}
