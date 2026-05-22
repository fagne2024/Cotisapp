package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.ModePaiement;
import com.cotisapp.domain.enums.TypeOperation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class OperationResponse {
    private Long id;
    private TypeOperation typeOperation;
    private Long membreId;
    private String membreNom;
    private BigDecimal montant;
    private BigDecimal montantFrais;
    /** Part créditée sur le compte solidarité membre (cotisations hebdo / mois). */
    private BigDecimal montantSolidarite;
    private LocalDate dateOperation;
    private String moisAnnee;
    private Long empruntId;
    private String observation;
    private ModePaiement modePaiement;
    private String referencePaiement;
    private List<MouvementPreviewResponse> mouvements;
}
