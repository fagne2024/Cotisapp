package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.StatutPlanad;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class JourneeReunionResponse {
    private Long id;
    private Integer numero;
    private LocalDate dateReunion;
    private String libelle;
    private StatutPlanad statut;
    private LocalDate dateCloture;
    private int nbOperations;
    private int nbCotisations;
    private int nbEmprunts;
    private int nbRemboursements;
}
