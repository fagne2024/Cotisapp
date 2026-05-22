package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class RecapJourneeResponse {
    private Long journeeId;
    private String codeOrganisation;
    private String libelle;
    private Integer numero;
    private LocalDate dateReunion;
    private RecapJourneeSyntheseResponse synthese;
    private List<RecapCompteResponse> comptesOrganisation;
    private List<RecapMembreResponse> membres;
    private List<RecapOperationLigneResponse> operations;
}
