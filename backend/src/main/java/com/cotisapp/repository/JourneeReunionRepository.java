package com.cotisapp.repository;

import com.cotisapp.domain.entity.JourneeReunion;
import com.cotisapp.domain.enums.StatutPlanad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JourneeReunionRepository extends JpaRepository<JourneeReunion, Long> {

    List<JourneeReunion> findByExerciceIdOrderByNumeroDesc(Long exerciceId);

    List<JourneeReunion> findByExerciceIdOrderByDateReunionAsc(Long exerciceId);

    Optional<JourneeReunion> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<JourneeReunion> findByExerciceIdAndDateReunion(Long exerciceId, LocalDate dateReunion);

    boolean existsByExerciceIdAndStatut(Long exerciceId, StatutPlanad statut);

    long countByExerciceIdAndStatut(Long exerciceId, StatutPlanad statut);

    @Query("""
            SELECT j FROM JourneeReunion j
            WHERE j.exerciceId = :exerciceId AND j.statut = com.cotisapp.domain.enums.StatutPlanad.OUVERT
            ORDER BY j.numero ASC
            """)
    Optional<JourneeReunion> findPlanadOuvert(@Param("exerciceId") Long exerciceId);

    @Query("SELECT COALESCE(MAX(j.numero), 0) FROM JourneeReunion j WHERE j.exerciceId = :exerciceId")
    int findMaxNumero(@Param("exerciceId") Long exerciceId);

    @Query("""
            SELECT DISTINCT o.dateOperation FROM Operation o
            WHERE o.organisationId = :orgId
              AND o.exerciceId = :exerciceId
              AND o.annulee = false
              AND o.operationOrigineId IS NULL
            ORDER BY o.dateOperation ASC
            """)
    List<LocalDate> findDistinctDatesOperations(
            @Param("orgId") Long orgId, @Param("exerciceId") Long exerciceId);
}
