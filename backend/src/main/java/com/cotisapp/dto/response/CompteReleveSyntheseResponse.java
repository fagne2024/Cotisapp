package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CompteReleveSyntheseResponse {
    private List<CompteOrgCardResponse> comptesOrganisation;
    private BigDecimal totalActifs;
    private BigDecimal encoursEmprunts;
    private long nbEmpruntsEnCours;
    private BigDecimal variationJourGlobale;
    private List<CompteMembreResumeResponse> membres;
}
