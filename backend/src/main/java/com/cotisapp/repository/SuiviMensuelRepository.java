package com.cotisapp.repository;

import com.cotisapp.domain.entity.SuiviMensuel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SuiviMensuelRepository extends JpaRepository<SuiviMensuel, Long> {
    List<SuiviMensuel> findByOrganisationId(Long organisationId);

    List<SuiviMensuel> findByOrganisationIdAndMoisAnnee(Long organisationId, String moisAnnee);

    List<SuiviMensuel> findByOrganisationIdAndExerciceIdAndMoisAnnee(
            Long organisationId, Long exerciceId, String moisAnnee);

    Optional<SuiviMensuel> findByMembreIdAndMoisAnnee(Long membreId, String moisAnnee);

    Optional<SuiviMensuel> findByExerciceIdAndMembreIdAndMoisAnnee(
            Long exerciceId, Long membreId, String moisAnnee);

    boolean existsByExerciceIdAndMembreIdAndMoisAnnee(Long exerciceId, Long membreId, String moisAnnee);

    void deleteByMembreId(Long membreId);
}
