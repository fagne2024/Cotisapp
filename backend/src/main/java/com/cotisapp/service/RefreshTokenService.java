package com.cotisapp.service;

import com.cotisapp.domain.entity.RefreshToken;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    @Value("${cotisapp.jwt.refresh-expiration-ms:2592000000}")
    private long refreshExpirationMs;

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public RefreshToken creer(Long utilisateurId, Role role, Long organisationId, Long membreId) {
        refreshTokenRepository.deleteAllByUtilisateurIdAndOrganisationId(utilisateurId, organisationId);

        RefreshToken rt = new RefreshToken();
        rt.setToken(UUID.randomUUID().toString());
        rt.setUtilisateurId(utilisateurId);
        rt.setRole(role);
        rt.setOrganisationId(organisationId);
        rt.setMembreId(membreId);
        rt.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));
        return refreshTokenRepository.save(rt);
    }

    /**
     * Valide le token, le révoque et en génère un nouveau (rotation).
     * Si un token déjà révoqué est présenté, tous les tokens de l'utilisateur sont invalidés
     * (signe de vol potentiel).
     */
    @Transactional
    public RefreshToken validerEtRoter(String token) {
        RefreshToken rt = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("Session expirée. Reconnectez-vous."));

        if (rt.isRevoked()) {
            refreshTokenRepository.deleteAllByUtilisateurId(rt.getUtilisateurId());
            throw new BusinessException("Session invalide. Reconnectez-vous.");
        }

        if (rt.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(rt);
            throw new BusinessException("Session expirée. Reconnectez-vous.");
        }

        rt.setRevoked(true);
        refreshTokenRepository.save(rt);
        return creer(rt.getUtilisateurId(), rt.getRole(), rt.getOrganisationId(), rt.getMembreId());
    }

    @Transactional
    public void revoquerParUtilisateur(Long utilisateurId) {
        refreshTokenRepository.deleteAllByUtilisateurId(utilisateurId);
    }
}
