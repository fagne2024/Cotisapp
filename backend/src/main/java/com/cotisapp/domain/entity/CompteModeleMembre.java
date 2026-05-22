package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "compte_modele_membre",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organisation_id", "code"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompteModeleMembre implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Builder.Default
    @Column(nullable = false)
    private Boolean actif = true;
}
