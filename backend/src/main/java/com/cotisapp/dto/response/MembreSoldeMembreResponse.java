package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Solde net du membre :
 * épargne + solidarité − emprunts − frais emprunt + remboursements + frais remboursement.
 */
@Data
@Builder
public class MembreSoldeMembreResponse {
    private Long membreId;
    private BigDecimal solde;
    private BigDecimal epargne;
    private BigDecimal solidarite;
    private BigDecimal emprunts;
    private BigDecimal fraisEmprunt;
    private BigDecimal remboursements;
    private BigDecimal fraisRemboursement;
}
