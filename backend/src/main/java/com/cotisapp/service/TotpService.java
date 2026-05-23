package com.cotisapp.service;

import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.dto.response.TwoFactorSetupResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.security.TotpSecretCipher;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TotpService {

    /** Secret TOTP Base32 (Google Authenticator), 16 à 32 caractères. */
    private static final Pattern PLAIN_TOTP_SECRET = Pattern.compile("^[A-Z2-7]{16,32}$");

    private final TotpSecretCipher totpSecretCipher;

    @Value("${cotisapp.totp.issuer:CotisApp}")
    private String issuer;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public TwoFactorSetupResponse buildSetupResponse(Utilisateur utilisateur, String plainSecret) {
        String label = utilisateur.getEmail() != null ? utilisateur.getEmail() : utilisateur.getPrenom() + " " + utilisateur.getNom();
        QrData data = new QrData.Builder()
                .label(label.trim())
                .secret(plainSecret)
                .issuer(issuer)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        String otpAuthUrl = data.getUri();
        String qrCodeDataUrl;
        try {
            QrGenerator qrGenerator = new ZxingPngQrGenerator();
            byte[] imageData = qrGenerator.generate(data);
            String mimeType = qrGenerator.getImageMimeType();
            qrCodeDataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageData);
        } catch (QrGenerationException e) {
            throw new BusinessException("Impossible de générer le QR code");
        }
        return TwoFactorSetupResponse.builder()
                .secret(plainSecret)
                .otpAuthUrl(otpAuthUrl)
                .qrCodeDataUrl(qrCodeDataUrl)
                .issuer(issuer)
                .build();
    }

    public boolean verifyPlainSecret(String plainSecret, String code) {
        if (plainSecret == null || plainSecret.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        String normalized = code.replaceAll("\\s", "");
        if (!normalized.matches("\\d{6}")) {
            return false;
        }
        return codeVerifier.isValidCode(plainSecret, normalized);
    }

    public boolean verifyUtilisateur(Utilisateur utilisateur, String code) {
        if (!Boolean.TRUE.equals(utilisateur.getTotpEnabled())) {
            return false;
        }
        String plain = resolvePlainSecret(utilisateur.getTotpSecret());
        return verifyPlainSecret(plain, code);
    }

    /** Indique si le secret en base est encore en clair (migration vers chiffrement AES). */
    public boolean isPlainSecretStored(String stored) {
        return stored != null && isPlainBase32Secret(normalizeBase32(stored));
    }

    /** Ré-enregistre le secret chiffré si la base contient encore du Base32 en clair. */
    public String encryptSecretIfNeeded(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        if (isPlainSecretStored(stored)) {
            return encryptSecret(normalizeBase32(stored));
        }
        return stored;
    }

    public String encryptSecret(String plainSecret) {
        return totpSecretCipher.encrypt(plainSecret);
    }

    public String decryptSecret(String encrypted) {
        return resolvePlainSecret(encrypted);
    }

    /**
     * Déchiffre le secret ou accepte un ancien enregistrement Base32 en clair.
     * Si le déchiffrement échoue (clé serveur changée), message orienté admin.
     */
    public String resolvePlainSecret(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String trimmed = stored.trim();
        if (isPlainBase32Secret(normalizeBase32(trimmed))) {
            return normalizeBase32(trimmed);
        }
        try {
            return totpSecretCipher.decrypt(trimmed);
        } catch (IllegalStateException e) {
            log.warn("Échec déchiffrement secret TOTP (clé ou format invalide): {}", e.getMessage());
            throw new BusinessException(
                    "La double authentification de ce compte doit être reconfigurée. "
                            + "Demandez à l'administrateur GIE de la réinitialiser (Utilisateurs & Droits), "
                            + "puis configurez-la à nouveau avec Google Authenticator.");
        }
    }

    private static String normalizeBase32(String value) {
        return value.replaceAll("\\s", "").toUpperCase();
    }

    private static boolean isPlainBase32Secret(String normalized) {
        return normalized != null && PLAIN_TOTP_SECRET.matcher(normalized).matches();
    }
}
