package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import com.cotisapp.domain.enums.Periodicite;
import com.cotisapp.domain.enums.TypeModeCalcul;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.domain.enums.UniteEcheance;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "regle_operation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegleOperation implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type_operation", nullable = false, length = 50)
    private TypeOperation typeOperation;

    @Column(nullable = false)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private Periodicite periodicite;

    @Column(name = "montant_min", precision = 19, scale = 2)
    private BigDecimal montantMin;

    @Column(name = "montant_max", precision = 19, scale = 2)
    private BigDecimal montantMax;

    /** Valeur d'une part en FCFA (cotisations). Montant = nb parts × montant_par_part. */
    @Column(name = "montant_par_part", precision = 19, scale = 2)
    private BigDecimal montantParPart;

    @Column(name = "parts_min")
    private Integer partsMin;

    @Column(name = "parts_max")
    private Integer partsMax;

    @Column(name = "solidarite_auto")
    @Builder.Default
    private Boolean solidariteAuto = false;

    @Column(name = "montant_solidarite_auto", precision = 19, scale = 2)
    private BigDecimal montantSolidariteAuto;

    @Column(name = "montant_amende_min", precision = 19, scale = 2)
    private BigDecimal montantAmendeMin;

    @Column(name = "montant_amende_max", precision = 19, scale = 2)
    private BigDecimal montantAmendeMax;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type_frais", length = 20)
    private TypeModeCalcul typeFrais;

    @Column(name = "montant_frais", precision = 19, scale = 2)
    private BigDecimal montantFrais;

    @Column(name = "pourcentage_frais", precision = 8, scale = 4)
    private BigDecimal pourcentageFrais;

    @Column(name = "nb_echeances_min")
    private Integer nbEcheancesMin;

    @Column(name = "nb_echeances_max")
    private Integer nbEcheancesMax;

    @Column(name = "nb_echeances_defaut")
    private Integer nbEcheancesDefaut;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "unite_echeance", length = 10)
    @Builder.Default
    private UniteEcheance uniteEcheance = UniteEcheance.MOIS;

    /** Jour du mois pour les dates d'échéance (1–31), optionnel, uniquement si uniteEcheance = MOIS. */
    @Column(name = "jour_echeance_mois")
    private Integer jourEcheanceMois;

    /** Nombre de jours avant l'échéance pour déclencher une alerte « proche » (emprunts uniquement). */
    @Column(name = "jours_alerte_echeance_proche")
    private Integer joursAlerteEcheanceProche;

    @Column(name = "montant_echeance_min", precision = 19, scale = 2)
    private BigDecimal montantEcheanceMin;

    @Column(name = "montant_echeance_max", precision = 19, scale = 2)
    private BigDecimal montantEcheanceMax;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type_penalite", length = 20)
    private TypeModeCalcul typePenalite;

    @Column(name = "montant_penalite", precision = 19, scale = 2)
    private BigDecimal montantPenalite;

    @Column(name = "pourcentage_penalite", precision = 8, scale = 4)
    private BigDecimal pourcentagePenalite;

    @Builder.Default
    private Boolean actif = true;

    @OneToMany(mappedBy = "regleOperation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<MouvementRegle> mouvements = new ArrayList<>();
}
