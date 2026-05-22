package com.cotisapp.repository;

import com.cotisapp.domain.entity.ReleveBancaire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReleveBancaireRepository extends JpaRepository<ReleveBancaire, Long> {

    List<ReleveBancaire> findByOperationIdIn(Collection<Long> operationIds);

    Optional<ReleveBancaire> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<ReleveBancaire> findByOperationId(Long operationId);
}
