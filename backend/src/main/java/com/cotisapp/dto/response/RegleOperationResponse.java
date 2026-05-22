package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.Periodicite;
import com.cotisapp.domain.enums.TypeModeCalcul;
import com.cotisapp.domain.enums.TypeOperation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class RegleOperationResponse {
    private Long id;
    private TypeOperation typeOperation;
    private String libelle;
    private Periodicite periodicite;
    private BigDecimal montantMin;
    private BigDecimal montantMax;
    private BigDecimal montantParPart;
    private Integer partsMin;
    private Integer partsMax;
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
    private Boolean actif;
    private List<MouvementRegleResponse> mouvements;
}
