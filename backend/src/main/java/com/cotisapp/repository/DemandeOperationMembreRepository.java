package com.cotisapp.repository;

import com.cotisapp.domain.entity.DemandeOperationMembre;
import com.cotisapp.domain.enums.DemandeOperationStatut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DemandeOperationMembreRepository extends JpaRepository<DemandeOperationMembre, Long> {

    List<DemandeOperationMembre> findByOrganisationIdAndStatutOrderByDateDemandeDesc(
            Long organisationId, DemandeOperationStatut statut);

    List<DemandeOperationMembre> findByOrganisationIdAndMembreIdAndStatutOrderByDateDemandeDesc(
            Long organisationId, Long membreId, DemandeOperationStatut statut);

    List<DemandeOperationMembre> findByOrganisationIdAndMembreIdAndStatutInOrderByDateDemandeDesc(
            Long organisationId, Long membreId, Collection<DemandeOperationStatut> statuts);

    Optional<DemandeOperationMembre> findByIdAndOrganisationId(Long id, Long organisationId);
}
