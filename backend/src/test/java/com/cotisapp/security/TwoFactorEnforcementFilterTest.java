package com.cotisapp.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TwoFactorEnforcementFilterTest {

    @Test
    void autorise_les_routes_de_configuration() {
        assertThat(TwoFactorEnforcementFilter.isAllowedDuringSetup("/api/me/2fa/setup", "POST")).isTrue();
        assertThat(TwoFactorEnforcementFilter.isAllowedDuringSetup("/api/me/2fa/confirm", "POST")).isTrue();
        assertThat(TwoFactorEnforcementFilter.isAllowedDuringSetup("/api/me", "GET")).isTrue();
        assertThat(TwoFactorEnforcementFilter.isAllowedDuringSetup("/api/auth/changer-mot-de-passe-initial", "POST")).isTrue();
    }

    @Test
    void bloque_le_reste() {
        assertThat(TwoFactorEnforcementFilter.isAllowedDuringSetup("/api/organisations/1/membres", "GET")).isFalse();
        assertThat(TwoFactorEnforcementFilter.isAllowedDuringSetup("/api/me/activite", "GET")).isFalse();
    }
}
