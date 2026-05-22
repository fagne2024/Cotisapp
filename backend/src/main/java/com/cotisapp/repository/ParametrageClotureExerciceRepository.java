package com.cotisapp.repository;

import com.cotisapp.domain.entity.ParametrageClotureExercice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParametrageClotureExerciceRepository extends JpaRepository<ParametrageClotureExercice, Long> {
    Optional<ParametrageClotureExercice> findByOrganisationId(Long organisationId);
}
