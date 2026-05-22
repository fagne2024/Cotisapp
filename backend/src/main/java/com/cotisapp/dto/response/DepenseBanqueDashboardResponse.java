package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DepenseBanqueDashboardResponse {
    private BigDecimal soldeCaisse;
    private BigDecimal soldeBanque;
    private BigDecimal totalDepensesMois;
    private List<DepenseLigneResponse> depensesRecentes;
    private List<DepenseParCategorieResponse> depensesParCategorie;
    private List<MouvementBanqueLigneResponse> mouvementsBanque;
    private BigDecimal entreesCaisseMois;
    private BigDecimal sortiesCaisseMois;
    private List<MouvementCaisseLigneResponse> mouvementsCaisse;
}
