package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.TypeEmprunt;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class EmpruntResponse {
    private Long id;
    private Long membreId;
    private String membreNom;
    private String codeMembre;
    private TypeEmprunt typeEmprunt;
    private BigDecimal montantTotal;
    private BigDecimal montantRembourse;
    private BigDecimal montantRestant;
    private BigDecimal montantFrais;
    /** Avance Caisse à l'octroi (Solidarité). */
    private BigDecimal montantAvanceCaisse;
    private BigDecimal montantRembourseAvanceCaisse;
    private BigDecimal montantAvanceCaisseRestant;
    private StatutEmprunt statut;
    private LocalDate dateCreation;
    private List<EcheanceResponse> echeances;
}
