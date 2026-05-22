package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.StatutSuiviMensuel;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SuiviMensuelResponse {
    private Long id;
    private Long membreId;
    private String membreNom;
    private String codeMembre;
    private String moisAnnee;
    private BigDecimal montantDu;
    private BigDecimal montantPaye;
    private StatutSuiviMensuel statut;
}
