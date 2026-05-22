package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class CotisationAnnulationResponse {
    private Long operationOrigineId;
    private Long operationAnnulationId;
    private String message;
    private LocalDate dateAnnulation;
    private int mouvementsInverses;
}
