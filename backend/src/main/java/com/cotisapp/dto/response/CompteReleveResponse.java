package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class CompteReleveResponse {
    private String scope;
    private Long compteId;
    private Long membreId;
    private String titre;
    private String meta;
    private String icone;
    private String iconeBg;
    private BigDecimal soldeActuel;
    /** Soldes par type (relevé membre uniquement). */
    private BigDecimal soldeSolidarite;
    private BigDecimal soldeDepense;
    private BigDecimal soldePenalitesAmendes;
    private BigDecimal variationJour;
    private BigDecimal entreesMois;
    private BigDecimal sortiesMois;
    private BigDecimal variationMois;
    /** Détail par compte (relevé caisse ou solidarité organisation). */
    private FluxCaisseSolidariteResponse fluxCaisseSolidarite;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private List<ReleveGroupeResponse> groupes;
    private ReleveTotauxResponse totaux;
}
