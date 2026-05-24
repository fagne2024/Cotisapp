package com.cotisapp.repository;

import com.cotisapp.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.utilisateurId = :utilisateurId")
    void deleteAllByUtilisateurId(Long utilisateurId);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.utilisateurId = :utilisateurId AND r.organisationId = :organisationId")
    void deleteAllByUtilisateurIdAndOrganisationId(Long utilisateurId, Long organisationId);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    void deleteAllExpired(Instant now);
}
