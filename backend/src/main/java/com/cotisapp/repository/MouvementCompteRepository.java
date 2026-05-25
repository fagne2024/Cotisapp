package com.cotisapp.repository;

import com.cotisapp.domain.entity.MouvementCompte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface MouvementCompteRepository extends JpaRepository<MouvementCompte, Long> {

    @Query("""
            SELECT m FROM MouvementCompte m
            JOIN FETCH m.operation o
            WHERE o.organisationId = :orgId
              AND m.compteId = :compteId
              AND o.dateOperation BETWEEN :debut AND :fin
            ORDER BY o.dateOperation DESC, o.dateCreation DESC, m.id DESC
            """)
    List<MouvementCompte> findByOrganisationAndCompteBetween(
            @Param("orgId") Long orgId,
            @Param("compteId") Long compteId,
            @Param("debut") LocalDate debut,
            @Param("fin") LocalDate fin);

    @Query("""
            SELECT m FROM MouvementCompte m
            JOIN FETCH m.operation o
            WHERE o.organisationId = :orgId
              AND m.compteId IN :compteIds
              AND o.dateOperation BETWEEN :debut AND :fin
            ORDER BY o.dateOperation DESC, o.dateCreation DESC, m.id DESC
            """)
    List<MouvementCompte> findByOrganisationAndComptesInBetween(
            @Param("orgId") Long orgId,
            @Param("compteIds") List<Long> compteIds,
            @Param("debut") LocalDate debut,
            @Param("fin") LocalDate fin);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN m.sens = com.cotisapp.domain.enums.SensMouvement.CREDIT
                THEN m.montant ELSE -m.montant END), 0)
            FROM MouvementCompte m
            JOIN m.operation o
            WHERE o.organisationId = :orgId
              AND m.compteId = :compteId
              AND o.dateOperation = :date
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            """)
    BigDecimal sumVariationComptePourDate(
            @Param("orgId") Long orgId,
            @Param("compteId") Long compteId,
            @Param("date") LocalDate date);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN m.sens = com.cotisapp.domain.enums.SensMouvement.CREDIT
                THEN m.montant ELSE -m.montant END), 0)
            FROM MouvementCompte m
            JOIN m.operation o
            WHERE o.organisationId = :orgId
              AND m.compteId IN :compteIds
              AND o.dateOperation = :date
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            """)
    BigDecimal sumVariationComptesPourDate(
            @Param("orgId") Long orgId,
            @Param("compteIds") List<Long> compteIds,
            @Param("date") LocalDate date);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN m.sens = com.cotisapp.domain.enums.SensMouvement.CREDIT
                THEN m.montant ELSE -m.montant END), 0)
            FROM MouvementCompte m
            JOIN m.operation o
            WHERE o.organisationId = :orgId
              AND m.compteId = :compteId
              AND o.dateOperation > :date
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            """)
    BigDecimal sumVariationCompteApresDate(
            @Param("orgId") Long orgId,
            @Param("compteId") Long compteId,
            @Param("date") LocalDate date);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN m.sens = com.cotisapp.domain.enums.SensMouvement.CREDIT
                THEN m.montant ELSE -m.montant END), 0)
            FROM MouvementCompte m
            JOIN m.operation o
            WHERE o.organisationId = :orgId
              AND m.compteId IN :compteIds
              AND o.dateOperation > :date
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            """)
    BigDecimal sumVariationComptesApresDate(
            @Param("orgId") Long orgId,
            @Param("compteIds") List<Long> compteIds,
            @Param("date") LocalDate date);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN m.sens = com.cotisapp.domain.enums.SensMouvement.CREDIT
                THEN m.montant ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN m.sens = com.cotisapp.domain.enums.SensMouvement.DEBIT
                THEN m.montant ELSE 0 END), 0)
            FROM MouvementCompte m
            JOIN m.operation o
            WHERE o.organisationId = :orgId
              AND m.compteId = :compteId
              AND o.dateOperation BETWEEN :debut AND :fin
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            """)
    Object[] sumEntreesSortiesComptePeriode(
            @Param("orgId") Long orgId,
            @Param("compteId") Long compteId,
            @Param("debut") LocalDate debut,
            @Param("fin") LocalDate fin);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN m.sens = com.cotisapp.domain.enums.SensMouvement.CREDIT
                THEN m.montant ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN m.sens = com.cotisapp.domain.enums.SensMouvement.DEBIT
                THEN m.montant ELSE 0 END), 0)
            FROM MouvementCompte m
            JOIN m.operation o
            WHERE o.organisationId = :orgId
              AND m.compteId IN :compteIds
              AND o.dateOperation BETWEEN :debut AND :fin
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            """)
    Object[] sumEntreesSortiesComptesPeriode(
            @Param("orgId") Long orgId,
            @Param("compteIds") List<Long> compteIds,
            @Param("debut") LocalDate debut,
            @Param("fin") LocalDate fin);
}
