package com.cotisapp.repository;

import com.cotisapp.domain.entity.CompteModeleMembre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompteModeleMembreRepository extends JpaRepository<CompteModeleMembre, Long> {

    List<CompteModeleMembre> findByOrganisationIdAndActifTrueOrderByLibelleAsc(Long organisationId);

    List<CompteModeleMembre> findByOrganisationIdOrderByLibelleAsc(Long organisationId);

    Optional<CompteModeleMembre> findByIdAndOrganisationId(Long id, Long organisationId);

    boolean existsByOrganisationIdAndCode(Long organisationId, String code);
}
