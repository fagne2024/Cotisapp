package com.cotisapp.repository;

import com.cotisapp.domain.entity.Exercice;
import com.cotisapp.domain.enums.StatutExercice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExerciceRepository extends JpaRepository<Exercice, Long> {

    List<Exercice> findByOrganisationIdOrderByNumeroDesc(Long organisationId);

    Optional<Exercice> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<Exercice> findByOrganisationIdAndStatut(Long organisationId, StatutExercice statut);

    @Query("SELECT COALESCE(MAX(e.numero), 0) FROM Exercice e WHERE e.organisationId = :orgId")
    int findMaxNumero(@Param("orgId") Long orgId);
}
