package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import com.cotisapp.domain.enums.StatutSuiviMensuel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "suivi_mensuel")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuiviMensuel implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "exercice_id", nullable = false)
    private Long exerciceId;

    @Column(name = "membre_id", nullable = false)
    private Long membreId;

    @Column(name = "mois_annee", nullable = false, length = 7)
    private String moisAnnee;

    @Column(name = "montant_du", nullable = false, precision = 19, scale = 2)
    private BigDecimal montantDu;

    @Column(name = "montant_paye", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal montantPaye = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutSuiviMensuel statut = StatutSuiviMensuel.NON_PAYE;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();

    @Column(name = "date_paiement")
    private LocalDate datePaiement;
}
