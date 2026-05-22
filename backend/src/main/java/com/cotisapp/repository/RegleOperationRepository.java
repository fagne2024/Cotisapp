package com.cotisapp.repository;

import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.enums.TypeOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegleOperationRepository extends JpaRepository<RegleOperation, Long> {
    List<RegleOperation> findByOrganisationId(Long organisationId);
    Optional<RegleOperation> findByOrganisationIdAndTypeOperationAndActifTrue(
            Long organisationId, TypeOperation typeOperation);

    boolean existsByOrganisationIdAndTypeOperation(Long organisationId, TypeOperation typeOperation);
}
