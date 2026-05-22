package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.StatutExercice;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ExerciceResponse {
    private Long id;
    private Long organisationId;
    private Integer numero;
    private StatutExercice statut;
    private LocalDate dateDebut;
    private LocalDate dateCloture;
    private Integer planadFin;
    private Boolean reinitialisationComptes;
    private String observationCloture;
    private boolean courant;
    private int nbPlanads;
    private int nbPlanadsOuverts;
    private boolean tousPlanadsClotures;
    private String planadOuvertLibelle;
}
