package com.cotisapp.repository;

import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.domain.enums.TypeOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OperationRepository extends JpaRepository<Operation, Long> {
    List<Operation> findByOrganisationIdOrderByDateCreationDesc(Long organisationId);
    List<Operation> findByMembreIdOrderByDateCreationDesc(Long membreId);

    @Query("""
            SELECT DISTINCT o FROM Operation o
            LEFT JOIN FETCH o.mouvements
            WHERE o.membreId = :membreId
            ORDER BY o.dateCreation DESC
            """)
    List<Operation> findByMembreIdWithMouvementsOrderByDateCreationDesc(@Param("membreId") Long membreId);

    long countByMembreId(Long membreId);

    List<Operation> findByMembreIdAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
            Long membreId, LocalDate debut, LocalDate fin);

    long countByOrganisationId(Long organisationId);

    long countByOrganisationIdAndExerciceId(Long organisationId, Long exerciceId);

    List<Operation> findTop10ByOrderByDateCreationDesc();

    List<Operation> findByOrganisationIdAndTypeOperationOrderByDateOperationDescDateCreationDesc(
            Long organisationId, TypeOperation typeOperation);

    List<Operation> findByOrganisationIdAndTypeOperationAndObservationContaining(
            Long organisationId, TypeOperation typeOperation, String observationFragment);

    List<Operation> findByOrganisationIdAndTypeOperationInAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
            Long organisationId,
            Collection<TypeOperation> types,
            LocalDate debut,
            LocalDate fin);

    List<Operation> findByOrganisationIdAndTypeOperationInOrderByDateOperationDescDateCreationDesc(
            Long organisationId,
            Collection<TypeOperation> typeOperations);

    List<Operation> findByOrganisationIdAndTypeOperationInAndOperationOrigineIdIsNullOrderByDateOperationDescDateCreationDesc(
            Long organisationId,
            Collection<TypeOperation> typeOperations);

    Optional<Operation> findByIdAndOrganisationId(Long id, Long organisationId);

    boolean existsByOperationOrigineId(Long operationOrigineId);

    boolean existsByEmpruntIdAndTypeOperationAndAnnuleeFalseAndOperationOrigineIdIsNull(
            Long empruntId, TypeOperation typeOperation);

    @Query("""
            SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
            FROM Operation o
            WHERE o.organisationId = :orgId
              AND o.membreId = :membreId
              AND o.typeOperation = :typeOperation
              AND o.dateOperation = :date
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            """)
    boolean existsOperationMembreMemeTypeMemeJour(
            @Param("orgId") Long orgId,
            @Param("membreId") Long membreId,
            @Param("typeOperation") TypeOperation typeOperation,
            @Param("date") LocalDate date);

    @Query("""
            SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
            FROM Operation o
            WHERE o.organisationId = :orgId
              AND o.membreId = :membreId
              AND o.typeOperation = com.cotisapp.domain.enums.TypeOperation.COTISATION
              AND o.observation LIKE CONCAT('%', :marqueurSemaine, '%')
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            """)
    boolean existsCotisationHebdoMembreAvecMarqueurSemaine(
            @Param("orgId") Long orgId,
            @Param("membreId") Long membreId,
            @Param("marqueurSemaine") String marqueurSemaine);

    @Query("""
            SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
            FROM Operation o
            WHERE o.organisationId = :orgId
              AND o.membreId = :membreId
              AND o.typeOperation = com.cotisapp.domain.enums.TypeOperation.COTISATION
              AND o.dateOperation BETWEEN :debut AND :fin
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            """)
    boolean existsCotisationHebdoMembreEntreDates(
            @Param("orgId") Long orgId,
            @Param("membreId") Long membreId,
            @Param("debut") LocalDate debut,
            @Param("fin") LocalDate fin);

    @Query("""
            SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
            FROM Operation o
            WHERE o.organisationId = :orgId
              AND o.membreId = :membreId
              AND o.typeOperation = com.cotisapp.domain.enums.TypeOperation.COTISATION_MOIS
              AND o.moisAnnee = :moisAnnee
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            """)
    boolean existsCotisationMoisMembrePourMois(
            @Param("orgId") Long orgId,
            @Param("membreId") Long membreId,
            @Param("moisAnnee") String moisAnnee);

    @Query("""
            SELECT o FROM Operation o
            WHERE o.organisationId = :orgId
              AND o.typeOperation = com.cotisapp.domain.enums.TypeOperation.COTISATION_MOIS
              AND o.moisAnnee = :moisAnnee
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            ORDER BY o.dateOperation DESC, o.dateCreation DESC
            """)
    List<Operation> findCotisationsMoisPourMois(
            @Param("orgId") Long orgId, @Param("moisAnnee") String moisAnnee);

    @Query("""
            SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
            FROM Operation o
            JOIN Emprunt e ON e.id = o.empruntId
            WHERE o.organisationId = :orgId
              AND o.membreId = :membreId
              AND o.typeOperation = com.cotisapp.domain.enums.TypeOperation.EMPRUNT
              AND o.dateOperation = :date
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
              AND e.typeEmprunt = :typeEmprunt
            """)
    boolean existsOctroiEmpruntMemeTypeMemeJour(
            @Param("orgId") Long orgId,
            @Param("membreId") Long membreId,
            @Param("typeEmprunt") TypeEmprunt typeEmprunt,
            @Param("date") LocalDate date);

    @Query("""
            SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
            FROM Operation o
            JOIN Emprunt e ON e.id = o.empruntId
            WHERE o.organisationId = :orgId
              AND o.membreId = :membreId
              AND o.typeOperation = com.cotisapp.domain.enums.TypeOperation.REMBOURSEMENT
              AND o.dateOperation = :date
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
              AND e.typeEmprunt = :typeEmprunt
            """)
    boolean existsRemboursementMemeTypeMemeJour(
            @Param("orgId") Long orgId,
            @Param("membreId") Long membreId,
            @Param("typeEmprunt") TypeEmprunt typeEmprunt,
            @Param("date") LocalDate date);

    List<Operation> findByEmpruntIdAndTypeOperationAndAnnuleeFalseAndOperationOrigineIdIsNullOrderByDateOperationDescDateCreationDesc(
            Long empruntId, TypeOperation typeOperation);

    @Query("""
            SELECT DISTINCT o FROM Operation o
            LEFT JOIN FETCH o.mouvements
            WHERE o.organisationId = :orgId
              AND o.exerciceId = :exerciceId
              AND o.dateOperation = :date
            ORDER BY o.dateCreation ASC, o.id ASC
            """)
    List<Operation> findByOrganisationIdAndExerciceIdAndDateOperationWithMouvements(
            @Param("orgId") Long orgId,
            @Param("exerciceId") Long exerciceId,
            @Param("date") LocalDate date);

    @Query("""
            SELECT MIN(o.dateOperation) FROM Operation o
            WHERE o.organisationId = :orgId
              AND o.exerciceId = :exerciceId
              AND o.dateOperation > :apresDate
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            """)
    Optional<LocalDate> findMinDateOperationApres(
            @Param("orgId") Long orgId,
            @Param("exerciceId") Long exerciceId,
            @Param("apresDate") LocalDate apresDate);

    List<Operation> findByOrganisationIdAndExerciceIdOrderByDateCreationDesc(
            Long organisationId, Long exerciceId);

    @Query("""
            SELECT COALESCE(SUM(COALESCE(o.montantFrais, 0)), 0)
            FROM Operation o
            WHERE o.organisationId = :orgId AND o.exerciceId = :exerciceId
              AND o.annulee = false
              AND o.typeOperation IN ('EMPRUNT', 'REMBOURSEMENT')
            """)
    BigDecimal sumFraisEmpruntByExercice(
            @Param("orgId") Long orgId, @Param("exerciceId") Long exerciceId);

    @Query("""
            SELECT COALESCE(SUM(mc.montant), 0)
            FROM MouvementCompte mc
            JOIN mc.operation o
            JOIN Compte c ON c.id = mc.compteId
            WHERE o.organisationId = :orgId AND o.exerciceId = :exerciceId
              AND o.annulee = false
              AND o.typeOperation IN ('COTISATION', 'COTISATION_MOIS')
              AND c.typeCompte = 'AMENDE'
              AND mc.sens = 'CREDIT'
            """)
    BigDecimal sumAmendesCotisationByExercice(
            @Param("orgId") Long orgId, @Param("exerciceId") Long exerciceId);

    @Query("""
            SELECT COALESCE(SUM(mc.montant), 0)
            FROM MouvementCompte mc
            JOIN mc.operation o
            JOIN Compte c ON c.id = mc.compteId
            WHERE o.organisationId = :orgId AND o.exerciceId = :exerciceId
              AND o.annulee = false
              AND o.typeOperation = 'REMBOURSEMENT'
              AND c.proprietaire = com.cotisapp.domain.enums.ProprietaireCompte.ORGANISATION
              AND mc.sens = 'CREDIT'
              AND o.observation LIKE 'Pénalité retard%'
            """)
    BigDecimal sumPenalitesRemboursementByExercice(
            @Param("orgId") Long orgId, @Param("exerciceId") Long exerciceId);

    @Query("""
            SELECT o.membreId, COALESCE(SUM(o.montant), 0), COUNT(o.id)
            FROM Operation o
            WHERE o.organisationId = :orgId AND o.exerciceId = :exerciceId
              AND o.annulee = false
              AND o.membreId IS NOT NULL
              AND o.typeOperation IN ('COTISATION', 'COTISATION_MOIS')
            GROUP BY o.membreId
            """)
    List<Object[]> sumCotisationsParMembreExercice(
            @Param("orgId") Long orgId, @Param("exerciceId") Long exerciceId);

    @Query("""
            SELECT COALESCE(SUM(o.montant), 0)
            FROM Operation o
            WHERE o.organisationId = :orgId AND o.exerciceId = :exerciceId
              AND o.annulee = false
              AND o.typeOperation = :typeOperation
            """)
    BigDecimal sumMontantByTypeOperationExercice(
            @Param("orgId") Long orgId,
            @Param("exerciceId") Long exerciceId,
            @Param("typeOperation") com.cotisapp.domain.enums.TypeOperation typeOperation);

    @Query("""
            SELECT MONTH(o.dateOperation), COALESCE(SUM(o.montant), 0)
            FROM Operation o
            WHERE o.organisationId = :orgId
              AND o.exerciceId = :exerciceId
              AND o.annulee = false
              AND o.typeOperation IN ('COTISATION', 'COTISATION_MOIS')
              AND YEAR(o.dateOperation) = :annee
            GROUP BY MONTH(o.dateOperation)
            ORDER BY MONTH(o.dateOperation)
            """)
    List<Object[]> sumCotisationsParMoisAnnee(
            @Param("orgId") Long orgId,
            @Param("exerciceId") Long exerciceId,
            @Param("annee") int annee);
}
