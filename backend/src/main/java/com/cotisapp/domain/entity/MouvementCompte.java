package com.cotisapp.domain.entity;

import com.cotisapp.domain.enums.SensMouvement;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "mouvement_compte")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MouvementCompte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id", nullable = false)
    private Operation operation;

    @Column(name = "compte_id", nullable = false)
    private Long compteId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private SensMouvement sens;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montant;
}
