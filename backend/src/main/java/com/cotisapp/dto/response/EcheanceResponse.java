package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.StatutEcheance;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class EcheanceResponse {
    private Long id;
    private Integer numero;
    private BigDecimal montantEcheance;
    private BigDecimal montantPaye;
    private LocalDate dateEcheance;
    private StatutEcheance statut;
}
