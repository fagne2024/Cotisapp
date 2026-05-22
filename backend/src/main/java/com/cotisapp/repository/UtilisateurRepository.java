package com.cotisapp.repository;

import com.cotisapp.domain.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByEmail(String email);
    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    Optional<Utilisateur> findByTelephoneNormalise(String telephoneNormalise);

    List<Utilisateur> findAllByTelephoneNormalise(String telephoneNormalise);
}
