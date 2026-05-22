package com.cotisapp.repository;

import com.cotisapp.domain.entity.TypeProfil;
import com.cotisapp.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TypeProfilRepository extends JpaRepository<TypeProfil, Long> {

    @Query("""
            SELECT t FROM TypeProfil t
            WHERE t.actif = true
              AND (t.organisationId IS NULL OR t.organisationId = :orgId)
            ORDER BY t.ordre, t.libelle
            """)
    List<TypeProfil> findDisponiblesPourOrganisation(@Param("orgId") Long orgId);

    List<TypeProfil> findByOrganisationIdOrderByOrdreAscLibelleAsc(Long organisationId);

    Optional<TypeProfil> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<TypeProfil> findFirstByOrganisationIdIsNullAndCodeOrderByIdAsc(String code);

    Optional<TypeProfil> findFirstByOrganisationIdAndCodeOrderByIdAsc(Long organisationId, String code);

    boolean existsByOrganisationIdAndCodeAndIdNot(Long organisationId, String code, Long id);

    Optional<TypeProfil> findFirstByOrganisationIdAndRoleAndPosteMembre(
            Long organisationId, Role role, com.cotisapp.domain.enums.PosteMembre posteMembre);

    Optional<TypeProfil> findFirstByOrganisationIdIsNullAndRole(Role role);
}
