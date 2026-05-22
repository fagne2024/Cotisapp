package com.cotisapp.repository;

import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.domain.enums.StatutEmprunt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmpruntRepository extends JpaRepository<Emprunt, Long> {

    @EntityGraph(attributePaths = "echeances")
    List<Emprunt> findByOrganisationId(Long organisationId);

    @EntityGraph(attributePaths = "echeances")
    List<Emprunt> findByOrganisationIdAndExerciceId(Long organisationId, Long exerciceId);

    @EntityGraph(attributePaths = "echeances")
    List<Emprunt> findByOrganisationIdAndExerciceIdAndTypeEmprunt(
            Long organisationId, Long exerciceId, TypeEmprunt typeEmprunt);

    @EntityGraph(attributePaths = "echeances")
    List<Emprunt> findByOrganisationIdAndTypeEmprunt(Long organisationId, TypeEmprunt typeEmprunt);

    @EntityGraph(attributePaths = "echeances")
    List<Emprunt> findByOrganisationIdAndStatut(Long organisationId, StatutEmprunt statut);

    @EntityGraph(attributePaths = "echeances")
    List<Emprunt> findByOrganisationIdAndStatutAndTypeEmprunt(
            Long organisationId, StatutEmprunt statut, TypeEmprunt typeEmprunt);

    @EntityGraph(attributePaths = "echeances")
    List<Emprunt> findByOrganisationIdAndStatutIn(
            Long organisationId, Collection<StatutEmprunt> statuts);

    @EntityGraph(attributePaths = "echeances")
    List<Emprunt> findByOrganisationIdAndStatutInAndTypeEmprunt(
            Long organisationId, Collection<StatutEmprunt> statuts, TypeEmprunt typeEmprunt);

    @EntityGraph(attributePaths = "echeances")
    Optional<Emprunt> findWithEcheancesByIdAndOrganisationId(Long id, Long organisationId);
    @EntityGraph(attributePaths = "echeances")
    List<Emprunt> findByMembreIdAndOrganisationId(Long membreId, Long organisationId);

    boolean existsByMembreIdAndOrganisationIdAndStatut(
            Long membreId, Long organisationId, StatutEmprunt statut);

    boolean existsByMembreIdAndOrganisationIdAndStatutAndTypeEmprunt(
            Long membreId, Long organisationId, StatutEmprunt statut, TypeEmprunt typeEmprunt);

    long countByMembreId(Long membreId);
    Optional<Emprunt> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByOrganisationId(Long organisationId);

    @Query("""
            SELECT COALESCE(SUM(e.montantTotal - COALESCE(e.montantRembourse, 0)), 0)
            FROM Emprunt e
            WHERE e.organisationId = :orgId AND e.statut = :statut
            """)
    BigDecimal sumEncoursByOrganisationIdAndStatut(
            @Param("orgId") Long orgId, @Param("statut") StatutEmprunt statut);
}
