package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.ModeAgregationPostesCloture;
import com.cotisapp.domain.enums.ModeCalculProrataCloture;
import com.cotisapp.domain.enums.ModeRepartitionCloture;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeModeCalcul;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ParametrageClotureExerciceResponse {
    private Long organisationId;
    private BigDecimal cotisationMontantMin;
    private BigDecimal cotisationMontantMax;
    private int partsMin;
    private int partsMax;
    private boolean partagerInterets;
    private boolean partagerPenalites;
    private boolean partagerAmendes;
    private ModeRepartitionCloture modeRepartition;
    private ModeAgregationPostesCloture modeAgregationPostes;
    private ModeCalculProrataCloture modeCalculProrata;
    private List<MembrePourcentageRepartitionResponse> pourcentagesRepartition;
    private boolean exclureMembresPretEnCours;
    private List<PostePartageClotureResponse> postesPartage;
    private TypeModeCalcul fraisClotureType;
    private BigDecimal fraisClotureValeur;
    private List<RetenueClotureRequestDto> retenues;
    private TypeCompte compteVersementMembre;
    private TypeCompte compteSourceOrg;

    @Data
    @Builder
    public static class RetenueClotureRequestDto {
        private String libelle;
        private TypeModeCalcul typeMode;
        private BigDecimal valeur;
        private int ordre;
    }
}
