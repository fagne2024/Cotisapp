package com.cotisapp.repository;

import com.cotisapp.domain.entity.JournalAudit;
import com.cotisapp.domain.enums.TypeEvenementJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JournalAuditRepository extends JpaRepository<JournalAudit, Long> {

    List<JournalAudit> findTop30ByUtilisateurIdOrderByDateCreationDesc(Long utilisateurId);

    List<JournalAudit> findTop30ByUtilisateurIdAndOrganisationIdOrderByDateCreationDesc(
            Long utilisateurId, Long organisationId);

    @Query("""
            SELECT j FROM JournalAudit j
            WHERE j.organisationId = :orgId
              AND (:utilisateurId IS NULL OR j.utilisateurId = :utilisateurId)
              AND (:type IS NULL OR j.typeEvenement = :type)
              AND (:succes IS NULL OR j.succes = :succes)
              AND (:search IS NULL OR :search = '' OR LOWER(j.action) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(j.details, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(j.utilisateurEmail, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(j.utilisateurNom, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(j.moduleLibelle, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY j.dateCreation DESC
            """)
    Page<JournalAudit> rechercherPourOrganisation(
            @Param("orgId") Long orgId,
            @Param("utilisateurId") Long utilisateurId,
            @Param("type") TypeEvenementJournal type,
            @Param("succes") Boolean succes,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT j.utilisateurId, MAX(j.dateCreation)
            FROM JournalAudit j
            WHERE j.organisationId = :orgId
              AND j.utilisateurId IS NOT NULL
              AND j.typeEvenement = com.cotisapp.domain.enums.TypeEvenementJournal.CONNEXION
              AND j.succes = true
            GROUP BY j.utilisateurId
            """)
    List<Object[]> findDerniereConnexionParUtilisateur(@Param("orgId") Long orgId);

    @Query("""
            SELECT j.utilisateurId, COUNT(j)
            FROM JournalAudit j
            WHERE j.organisationId = :orgId
              AND j.utilisateurId IS NOT NULL
              AND j.typeEvenement = com.cotisapp.domain.enums.TypeEvenementJournal.CONNEXION
              AND j.succes = true
              AND j.dateCreation >= :depuis
            GROUP BY j.utilisateurId
            """)
    List<Object[]> countConnexions30jParUtilisateur(
            @Param("orgId") Long orgId, @Param("depuis") LocalDateTime depuis);

    long countByOrganisationId(Long organisationId);
}
