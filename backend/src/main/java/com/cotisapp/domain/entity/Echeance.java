package com.cotisapp.domain.entity;

import com.cotisapp.domain.enums.StatutEcheance;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "echeance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Echeance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emprunt_id", nullable = false)
    private Emprunt emprunt;

    @Column(nullable = false)
    private Integer numero;

    @Column(name = "montant_echeance", nullable = false, precision = 19, scale = 2)
    private BigDecimal montantEcheance;

    @Column(name = "montant_paye", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal montantPaye = BigDecimal.ZERO;

    @Column(name = "date_echeance", nullable = false)
    private LocalDate dateEcheance;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutEcheance statut = StatutEcheance.A_PAYER;

    @Column(name = "date_paiement")
    private LocalDate datePaiement;
}
