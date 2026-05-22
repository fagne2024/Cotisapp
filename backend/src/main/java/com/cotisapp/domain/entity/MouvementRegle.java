package com.cotisapp.domain.entity;

import com.cotisapp.domain.enums.SensMouvement;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mouvement_regle")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MouvementRegle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regle_operation_id", nullable = false)
    private RegleOperation regleOperation;

    @Column(nullable = false)
    private Integer ordre;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "cible_type", nullable = false, length = 50)
    private String cibleType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private SensMouvement sens;

    @Column(name = "type_montant", nullable = false, length = 20)
    @Builder.Default
    private String typeMontant = "MONTANT_SAISI";
}
