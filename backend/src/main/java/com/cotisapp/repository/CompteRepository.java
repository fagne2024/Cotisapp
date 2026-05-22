package com.cotisapp.repository;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.TypeCompte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompteRepository extends JpaRepository<Compte, Long> {
    List<Compte> findByOrganisationId(Long organisationId);
    List<Compte> findByMembreId(Long membreId);
    Optional<Compte> findByOrganisationIdAndTypeCompteAndProprietaire(
            Long organisationId, TypeCompte typeCompte, ProprietaireCompte proprietaire);
    Optional<Compte> findByMembreIdAndTypeCompte(Long membreId, TypeCompte typeCompte);

    boolean existsByMembreIdAndModeleCompteId(Long membreId, Long modeleCompteId);

    Optional<Compte> findByMembreIdAndModeleCompteId(Long membreId, Long modeleCompteId);
}
