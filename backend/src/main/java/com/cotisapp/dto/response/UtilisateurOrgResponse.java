package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UtilisateurOrgResponse {
    private Long utilisateurId;
    private Long roleId;
    private Long membreId;
    private String email;
    private String telephone;
    private String nom;
    private String prenom;
    private String nomComplet;
    private Role role;
    private PosteMembre poste;
    /** Code du profil applicatif (SG, SGA, TRESORIER…). */
    private String typeProfilCode;
    private String typeProfilLibelle;
    private String codeMembre;
    private Boolean actif;
    private String derniereConnexionLibelle;
    private int connexions30j;
    private boolean enLigne;
}
