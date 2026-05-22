package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.ModeAgregationPostesCloture;
import com.cotisapp.domain.enums.ModeCalculProrataCloture;
import com.cotisapp.domain.enums.ModeRepartitionCloture;
import com.cotisapp.dto.request.cloture.MembrePourcentageRepartitionRequest;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeModeCalcul;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ParametrageClotureExerciceRequest {
    @NotNull
    private BigDecimal cotisationMontantMin;
    @NotNull
    private BigDecimal cotisationMontantMax;
    @NotNull
    private Integer partsMin;
    @NotNull
    private Integer partsMax;
    private Boolean partagerInterets;
    private Boolean partagerPenalites;
    private Boolean partagerAmendes;
    private ModeRepartitionCloture modeRepartition;
    private ModeAgregationPostesCloture modeAgregationPostes;
    private ModeCalculProrataCloture modeCalculProrata;
    private List<MembrePourcentageRepartitionRequest> pourcentagesRepartition;
    private Boolean exclureMembresPretEnCours;
    private List<PostePartageClotureRequest> postesPartage;
    @NotNull
    private TypeModeCalcul fraisClotureType;
    @NotNull
    private BigDecimal fraisClotureValeur;
    @Valid
    private List<RetenueClotureRequest> retenues;
    @NotNull
    private TypeCompte compteVersementMembre;
    @NotNull
    private TypeCompte compteSourceOrg;
}
