package com.cotisapp.repository;

import com.cotisapp.domain.entity.Echeance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EcheanceRepository extends JpaRepository<Echeance, Long> {
    Optional<Echeance> findByIdAndEmpruntId(Long id, Long empruntId);
}
