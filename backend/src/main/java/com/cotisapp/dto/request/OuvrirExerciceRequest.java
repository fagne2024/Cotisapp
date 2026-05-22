package com.cotisapp.dto.request;

import lombok.Data;

@Data
public class OuvrirExerciceRequest {
    /** Si true, remet tous les soldes de comptes à zéro pour le nouvel exercice. */
    private Boolean reinitialiserComptes;
    private String observationCloture;
    /** Si true, répartit intérêts / pénalités / amendes aux membres (prorata parts) avant la transition. */
    private Boolean effectuerRepartition;
}
