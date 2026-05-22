package com.cotisapp.repository;

import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UtilisateurRoleRepository extends JpaRepository<UtilisateurRole, Long> {
    List<UtilisateurRole> findByUtilisateurId(Long utilisateurId);
    Optional<UtilisateurRole> findFirstByUtilisateurIdOrderByIdAsc(Long utilisateurId);
    Optional<UtilisateurRole> findFirstByUtilisateurIdAndRoleOrderByIdAsc(Long utilisateurId, Role role);

    Optional<UtilisateurRole> findFirstByOrganisationIdAndRole(Long organisationId, Role role);

    List<UtilisateurRole> findByOrganisationId(Long organisationId);

    Optional<UtilisateurRole> findFirstByUtilisateurIdAndOrganisationIdOrderByIdAsc(
            Long utilisateurId, Long organisationId);

    Optional<UtilisateurRole> findFirstByUtilisateurIdAndRoleAndOrganisationIdOrderByIdAsc(
            Long utilisateurId, Role role, Long organisationId);

    long countByTypeProfilId(Long typeProfilId);

    List<UtilisateurRole> findByMembreId(Long membreId);

    Optional<UtilisateurRole> findFirstByMembreIdAndRole(Long membreId, Role role);

    void deleteByMembreId(Long membreId);
}
