package com.cotisapp.repository;

import com.cotisapp.domain.entity.ParametrageCompteOrganisation;
import com.cotisapp.domain.enums.FamilleCompte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParametrageCompteOrganisationRepository extends JpaRepository<ParametrageCompteOrganisation, Long> {

    List<ParametrageCompteOrganisation> findByOrganisationIdOrderByFamilleAsc(Long organisationId);

    Optional<ParametrageCompteOrganisation> findByOrganisationIdAndFamille(Long organisationId, FamilleCompte famille);

    boolean existsByOrganisationId(Long organisationId);
}
