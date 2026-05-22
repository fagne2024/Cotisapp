package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import com.cotisapp.domain.enums.FamilleCompte;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.TypeCompte;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "parametrage_compte_organisation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organisation_id", "famille"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametrageCompteOrganisation implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private FamilleCompte famille;

    @Column(nullable = false)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type_compte", nullable = false, length = 50)
    private TypeCompte typeCompte;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ProprietaireCompte proprietaire;

    @Builder.Default
    @Column(nullable = false)
    private Boolean actif = true;
}
