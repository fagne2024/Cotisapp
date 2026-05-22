package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrganisationResumeResponse {
    private Long id;
    private String code;
    private String nom;
    private String description;
    private String logoUrl;
    private boolean actif;
    private LocalDateTime dateCreation;
    private Long adminUtilisateurId;
    private String adminPrenom;
    private String adminNom;
    private String adminEmail;
    private boolean adminActif;
    private boolean adminTwoFactorEnabled;
    private long nbMembres;
    private long nbMembresBureau;
    private long nbMembresSimples;
    private BigDecimal soldeCaisse;
    private BigDecimal soldeSolidarite;
    private BigDecimal soldeBanque;
    private long nbEmpruntsActifs;
    private long nbEmpruntsEnRetard;
    private long nbRegles;
}
