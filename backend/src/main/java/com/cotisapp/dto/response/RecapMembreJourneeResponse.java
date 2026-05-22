package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class RecapMembreJourneeResponse {
    private Long journeeId;
    private String libelle;
    private Integer numero;
    private LocalDate dateReunion;
    private RecapMembreResponse resume;
    private RecapJourneeSyntheseResponse synthese;
    private List<RecapOperationLigneResponse> operations;
}
