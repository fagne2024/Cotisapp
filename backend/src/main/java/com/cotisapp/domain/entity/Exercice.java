package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import com.cotisapp.domain.enums.StatutExercice;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exercice")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Exercice implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private Integer numero;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutExercice statut = StatutExercice.EN_COURS;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_cloture")
    private LocalDate dateCloture;

    /** Dernier numéro PLANAD de l'exercice (renseigné à la clôture). */
    @Column(name = "planad_fin")
    private Integer planadFin;

    @Column(name = "reinitialisation_comptes", nullable = false)
    @Builder.Default
    private Boolean reinitialisationComptes = false;

    @Column(name = "observation_cloture", length = 500)
    private String observationCloture;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
