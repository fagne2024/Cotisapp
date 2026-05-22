package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.TypeEmprunt;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "emprunt")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Emprunt implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "exercice_id", nullable = false)
    private Long exerciceId;

    @Column(name = "membre_id", nullable = false)
    private Long membreId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type_emprunt", nullable = false, length = 20)
    private TypeEmprunt typeEmprunt;

    @Column(name = "montant_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal montantTotal;

    @Column(name = "montant_rembourse", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal montantRembourse = BigDecimal.ZERO;

    @Column(name = "montant_frais", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal montantFrais = BigDecimal.ZERO;

    /** Montant avancé depuis la Caisse lors de l'octroi (emprunt Solidarité uniquement). */
    @Column(name = "montant_avance_caisse", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal montantAvanceCaisse = BigDecimal.ZERO;

    /** Part de l'avance Caisse déjà remboursée à la Caisse. */
    @Column(name = "montant_rembourse_avance_caisse", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal montantRembourseAvanceCaisse = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutEmprunt statut = StatutEmprunt.EN_COURS;

    @Column(name = "date_creation", nullable = false)
    private LocalDate dateCreation;

    private String observation;

    @OneToMany(mappedBy = "emprunt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Echeance> echeances = new ArrayList<>();
}
