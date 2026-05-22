package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CotisationSuiviMembreResponse {
    private Long membreId;
    private String nomComplet;
    private String codeMembre;
    private String poste;
    private String sousTitre;
    /** PAYE ou ATTENTE */
    private String statut;
}
