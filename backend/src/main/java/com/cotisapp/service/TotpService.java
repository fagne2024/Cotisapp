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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TotpService {

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
        String plain = decryptSecret(utilisateur.getTotpSecret());
        return verifyPlainSecret(plain, code);
    }

    public String encryptSecret(String plainSecret) {
        return totpSecretCipher.encrypt(plainSecret);
    }

    public String decryptSecret(String encrypted) {
        return totpSecretCipher.decrypt(encrypted);
    }
}
