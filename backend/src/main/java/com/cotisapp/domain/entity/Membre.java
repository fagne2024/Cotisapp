package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import jakarta.persistence.*;
import lombok.*;

import com.cotisapp.domain.enums.PosteMembre;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "membre")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Membre implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "utilisateur_id")
    private Long utilisateurId;

    @Column(name = "code_membre", nullable = false, length = 50)
    private String codeMembre;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    private String telephone;

    @Column(name = "telephone_normalise", length = 20)
    private String telephoneNormalise;

    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PosteMembre poste = PosteMembre.SIMPLE;

    @Column(name = "date_adhesion")
    private LocalDate dateAdhesion;

    @Column(name = "piece_identite", length = 80)
    private String pieceIdentite;

    @Builder.Default
    private Boolean actif = true;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();

    public String getNomComplet() {
        return prenom + " " + nom;
    }
}
