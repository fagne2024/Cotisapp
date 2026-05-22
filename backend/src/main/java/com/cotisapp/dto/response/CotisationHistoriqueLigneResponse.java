package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CotisationHistoriqueLigneResponse {
    /** Identifiant unique de ligne (opération + type). */
    private String ligneId;
    private Long operationId;
    /** HEBDO, MOIS, SOLIDARITE */
    private String typeLigne;
    /** Cotisation parente : HEBDO ou MOIS (y compris pour les lignes solidarité). */
    private String typeCotisation;
    private String typeLibelle;
    private Long membreId;
    private String membreNom;
    private String codeMembre;
    private String periode;
    private BigDecimal montant;
    private LocalDate dateOperation;
    private String dateLabel;
    private String observation;
    private String modePaiementLibelle;
    private String referencePaiement;
    private boolean annulee;
    /** Annulation possible (ligne cotisation principale non annulée). */
    private boolean annulable;
}
