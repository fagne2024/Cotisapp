package com.cotisapp.repository;

import com.cotisapp.domain.entity.NotificationEtat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationEtatRepository extends JpaRepository<NotificationEtat, Long> {

    List<NotificationEtat> findByOrganisationIdAndUtilisateurId(Long organisationId, Long utilisateurId);

    Optional<NotificationEtat> findByOrganisationIdAndUtilisateurIdAndCleNotification(
            Long organisationId, Long utilisateurId, String cleNotification);
}
