package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.CanalConnexion;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ProfilResponse {
    private Long userId;
    private String email;
    private String prenom;
    private String nom;
    private String nomComplet;
    private Role role;
    private String roleLabel;
    private Long typeProfilId;
    private String typeProfilCode;
    private String typeProfilLibelle;
    private CanalConnexion canalConnexion;
    private String identifiantConnexion;
    private Long organisationId;
    private String organisationNom;
    private Long membreId;
    private String codeMembre;
    private PosteMembre posteMembre;
    private String posteLabel;
    private String telephone;
    private String telephoneSecondaire;
    private String adresse;
    private LocalDate dateAdhesion;
    private LocalDateTime dateCreation;
    private boolean actif;
    private boolean superadminSansOrg;
    private boolean twoFactorEnabled;
}
