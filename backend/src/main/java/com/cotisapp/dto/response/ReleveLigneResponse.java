package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class ReleveLigneResponse {
    private Long operationId;
    private LocalDate dateOperation;
    private LocalTime heureOperation;
    private String titre;
    private String typeOperation;
    private String typeLibelle;
    private String typeTagClass;
    private String sens;
    private BigDecimal montant;
    private BigDecimal soldeApres;
    private boolean annulee;
    private boolean contrepassation;
    private String reference;
    private String membreNom;
    private String codeMembre;
    private String icone;
    private String iconeBg;
    private String metaExtra;
}
