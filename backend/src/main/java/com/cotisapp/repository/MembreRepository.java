package com.cotisapp.repository;

import com.cotisapp.domain.entity.Membre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MembreRepository extends JpaRepository<Membre, Long> {
    List<Membre> findByOrganisationIdAndActifTrue(Long organisationId);
    List<Membre> findByOrganisationId(Long organisationId);

    long countByOrganisationId(Long organisationId);
    Optional<Membre> findByIdAndOrganisationId(Long id, Long organisationId);
    boolean existsByUtilisateurIdAndOrganisationId(Long utilisateurId, Long organisationId);
    Optional<Membre> findFirstByUtilisateurIdAndOrganisationIdOrderByIdAsc(Long utilisateurId, Long organisationId);

    @Query("""
            SELECT m FROM Membre m
            WHERE m.organisationId = :orgId AND m.actif = true
            AND (
                LOWER(m.codeMembre) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(m.nom) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(m.prenom) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(CONCAT(m.prenom, ' ', m.nom)) LIKE LOWER(CONCAT('%', :q, '%'))
                OR (m.telephone IS NOT NULL AND LOWER(m.telephone) LIKE LOWER(CONCAT('%', :q, '%')))
            )
            ORDER BY m.nom, m.prenom
            """)
    List<Membre> rechercherActifs(@Param("orgId") Long orgId, @Param("q") String q);

    List<Membre> findByTelephoneNormaliseAndActifTrue(String telephoneNormalise);

    List<Membre> findByActifTrueAndUtilisateurIdIsNotNull();

    Optional<Membre> findByTelephoneNormaliseAndOrganisationIdAndActifTrue(
            String telephoneNormalise, Long organisationId);

    boolean existsByOrganisationIdAndEmailIgnoreCase(Long organisationId, String email);

    boolean existsByOrganisationIdAndTelephoneNormalise(Long organisationId, String telephoneNormalise);

    boolean existsByOrganisationIdAndPieceIdentiteIgnoreCase(Long organisationId, String pieceIdentite);
}
