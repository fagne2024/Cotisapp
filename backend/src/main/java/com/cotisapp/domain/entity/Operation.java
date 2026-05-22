package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import com.cotisapp.domain.enums.ModePaiement;
import com.cotisapp.domain.enums.TypeOperation;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "operation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Operation implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "exercice_id", nullable = false)
    private Long exerciceId;

    @Column(name = "membre_id")
    private Long membreId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type_operation", nullable = false, length = 50)
    private TypeOperation typeOperation;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montant;

    @Column(name = "montant_frais", precision = 19, scale = 2)
    private BigDecimal montantFrais;

    @Column(name = "date_operation", nullable = false)
    private LocalDate dateOperation;

    private String observation;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode_paiement", length = 30)
    private ModePaiement modePaiement;

    @Column(name = "reference_paiement", length = 120)
    private String referencePaiement;

    @Column(name = "emprunt_id")
    private Long empruntId;

    @Column(name = "echeance_id")
    private Long echeanceId;

    @Column(name = "mois_annee", length = 7)
    private String moisAnnee;

    @Column(name = "utilisateur_id", nullable = false)
    private Long utilisateurId;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private Boolean annulee = false;

    /** Renseigné sur l'opération d'annulation (contre-passation). */
    @Column(name = "operation_origine_id")
    private Long operationOrigineId;

    @OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MouvementCompte> mouvements = new ArrayList<>();
}
