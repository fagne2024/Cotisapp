package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.PosteMembre;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class MembreResponse {
    private Long id;
    private String codeMembre;
    private String nom;
    private String prenom;
    private String nomComplet;
    private Boolean actif;
    private String telephone;
    private String email;
    private PosteMembre poste;
    private LocalDate dateAdhesion;
    private String pieceIdentite;
    private LocalDateTime dateCreation;
    private Long utilisateurId;
    private boolean compteAcces;
    /** Paiement mobile money autorisé pour « Mon compte » (activé par l'admin GIE). */
    private boolean paiementMobileActif;
}
