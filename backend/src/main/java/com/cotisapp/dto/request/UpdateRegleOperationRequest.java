package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.Periodicite;
import com.cotisapp.domain.enums.TypeModeCalcul;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateRegleOperationRequest {
    @NotBlank
    private String libelle;

    private Periodicite periodicite;

    private BigDecimal montantMin;

    private BigDecimal montantMax;

    private BigDecimal montantParPart;
    private Integer partsMin;
    private Integer partsMax;

    @NotNull
    private Boolean solidariteAuto;

    private BigDecimal montantSolidariteAuto;

    private BigDecimal montantAmendeMin;

    private BigDecimal montantAmendeMax;

    private TypeModeCalcul typeFrais;
    private BigDecimal montantFrais;
    private BigDecimal pourcentageFrais;
    private Integer nbEcheancesMin;
    private Integer nbEcheancesMax;
    private Integer nbEcheancesDefaut;
    private Integer jourEcheanceMois;
    private BigDecimal montantEcheanceMin;
    private BigDecimal montantEcheanceMax;
    private TypeModeCalcul typePenalite;
    private BigDecimal montantPenalite;
    private BigDecimal pourcentagePenalite;

    @NotNull
    private Boolean actif;

    @Valid
    @NotNull
    private List<MouvementRegleRequest> mouvements;
}
