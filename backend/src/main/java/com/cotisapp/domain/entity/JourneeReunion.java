package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import com.cotisapp.domain.enums.StatutPlanad;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "journee_reunion")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneeReunion implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "exercice_id", nullable = false)
    private Long exerciceId;

    @Column(nullable = false)
    private Integer numero;

    @Column(name = "date_reunion", nullable = false)
    private LocalDate dateReunion;

    @Column(nullable = false, length = 80)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutPlanad statut = StatutPlanad.OUVERT;

    @Column(name = "date_cloture")
    private LocalDate dateCloture;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
