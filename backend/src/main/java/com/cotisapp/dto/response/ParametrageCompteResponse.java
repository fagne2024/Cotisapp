package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.FamilleCompte;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.TypeCompte;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ParametrageCompteResponse {
    private FamilleCompte famille;
    private String libelle;
    private TypeCompte typeCompte;
    private ProprietaireCompte proprietaire;
    private Boolean actif;
    /** Solde du compte organisation (caisse / solidarité) ; null pour les comptes membres. */
    private BigDecimal soldeOrganisation;
    private String description;
}
