package com.cotisapp.domain.entity;

import com.cotisapp.domain.enums.ModeAgregationPostesCloture;
import com.cotisapp.domain.enums.ModeCalculProrataCloture;
import com.cotisapp.domain.enums.ModeRepartitionCloture;
import com.cotisapp.domain.enums.TypeModeCalcul;
import com.cotisapp.domain.enums.TypeCompte;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "parametrage_cloture_exercice")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametrageClotureExercice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false, unique = true)
    private Long organisationId;

    @Column(name = "cotisation_montant_min", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal cotisationMontantMin = new BigDecimal("1000");

    @Column(name = "cotisation_montant_max", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal cotisationMontantMax = new BigDecimal("10000");

    @Column(name = "parts_min", nullable = false)
    @Builder.Default
    private Integer partsMin = 1;

    @Column(name = "parts_max", nullable = false)
    @Builder.Default
    private Integer partsMax = 10;

    @Column(name = "partager_interets", nullable = false)
    @Builder.Default
    private Boolean partagerInterets = true;

    @Column(name = "partager_penalites", nullable = false)
    @Builder.Default
    private Boolean partagerPenalites = true;

    @Column(name = "partager_amendes", nullable = false)
    @Builder.Default
    private Boolean partagerAmendes = true;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode_repartition", nullable = false, length = 20)
    @Builder.Default
    private ModeRepartitionCloture modeRepartition = ModeRepartitionCloture.PRORATA;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode_agregation_postes", nullable = false, length = 20)
    @Builder.Default
    private ModeAgregationPostesCloture modeAgregationPostes = ModeAgregationPostesCloture.SEPARER;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode_calcul_prorata", nullable = false, length = 20)
    @Builder.Default
    private ModeCalculProrataCloture modeCalculProrata = ModeCalculProrataCloture.PARTS;

    @Column(name = "pourcentages_repartition_json", columnDefinition = "TEXT")
    private String pourcentagesRepartitionJson;

    @Column(name = "exclure_membres_pret_en_cours", nullable = false)
    @Builder.Default
    private Boolean exclureMembresPretEnCours = false;

    @Column(name = "postes_partage_json", columnDefinition = "TEXT")
    private String postesPartageJson;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "frais_cloture_type", nullable = false, length = 20)
    @Builder.Default
    private TypeModeCalcul fraisClotureType = TypeModeCalcul.FIXE;

    @Column(name = "frais_cloture_valeur", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal fraisClotureValeur = BigDecimal.ZERO;

    @Column(name = "retenues_json", columnDefinition = "TEXT")
    private String retenuesJson;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "compte_versement_membre", nullable = false, length = 40)
    @Builder.Default
    private TypeCompte compteVersementMembre = TypeCompte.EPARGNE_HEBDO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "compte_source_org", nullable = false, length = 40)
    @Builder.Default
    private TypeCompte compteSourceOrg = TypeCompte.CAISSE;
}
