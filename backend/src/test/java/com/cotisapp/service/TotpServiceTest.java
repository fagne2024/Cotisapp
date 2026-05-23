package com.cotisapp.service;

import com.cotisapp.security.TotpSecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        TotpSecretCipher cipher = new TotpSecretCipher("test-key-for-totp-encryption-32chars!!");
        totpService = new TotpService(cipher);
        org.springframework.test.util.ReflectionTestUtils.setField(totpService, "issuer", "CotisApp");
    }

    @Test
    void secret_genere_et_code_verifiable() {
        String secret = totpService.generateSecret();
        assertThat(secret).isNotBlank();
        String encrypted = totpService.encryptSecret(secret);
        String decrypted = totpService.decryptSecret(encrypted);
        assertThat(decrypted).isEqualTo(secret);
    }

    @Test
    void resolvePlainSecret_acceptsLegacyBase32InClear() {
        String plain = "JBSWY3DPEHPK3PXP";
        assertThat(totpService.resolvePlainSecret(plain)).isEqualTo(plain);
        assertThat(totpService.isPlainSecretStored(plain)).isTrue();
    }
}
