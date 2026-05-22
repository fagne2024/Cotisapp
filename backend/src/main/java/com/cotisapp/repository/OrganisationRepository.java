package com.cotisapp.repository;

import com.cotisapp.domain.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganisationRepository extends JpaRepository<Organisation, Long> {
    Optional<Organisation> findByCode(String code);
    boolean existsByCode(String code);
}
