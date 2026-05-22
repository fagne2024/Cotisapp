package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "releve_bancaire")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleveBancaire implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "operation_id", nullable = false, unique = true)
    private Long operationId;

    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    @Column(name = "chemin_stockage", nullable = false, length = 500)
    private String cheminStockage;

    @Column(name = "type_mime", length = 100)
    private String typeMime;

    @Column(name = "taille_octets", nullable = false)
    private Long tailleOctets;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
