package com.cotisapp.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "organisation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String nom;

    private String description;

    @Column(name = "logo_chemin", length = 512)
    private String logoChemin;

    @Builder.Default
    private Boolean actif = true;

    @Column(name = "exercice_courant_id")
    private Long exerciceCourantId;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
