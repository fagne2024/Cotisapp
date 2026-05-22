package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.TypeCompte;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "compte")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Compte implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id")
    private Long organisationId;

    @Column(name = "membre_id")
    private Long membreId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type_compte", nullable = false, length = 50)
    private TypeCompte typeCompte;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ProprietaireCompte proprietaire;

    @Builder.Default
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal solde = BigDecimal.ZERO;

    @Builder.Default
    private Boolean actif = true;

    /** Libellé affiché (synchronisé avec le paramétrage organisation pour les comptes org). */
    private String libelle;

    @Column(name = "modele_compte_id")
    private Long modeleCompteId;
}
