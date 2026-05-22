package com.cotisapp.security;

import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.enums.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpPolicyTest {

    @Test
    void admin_sans_2fa_doit_configurer() {
        Utilisateur u = Utilisateur.builder().totpEnabled(false).build();
        assertThat(TotpPolicy.mustSetupTwoFactor(Role.ADMIN_GIE, u)).isTrue();
        assertThat(TotpPolicy.mustSetupTwoFactor(Role.SUPERADMIN, u)).isTrue();
        assertThat(TotpPolicy.mustSetupTwoFactor(Role.MEMBRE, u)).isFalse();
    }

    @Test
    void admin_avec_2fa_ne_doit_pas_configurer() {
        Utilisateur u = Utilisateur.builder().totpEnabled(true).totpSecret("enc").build();
        assertThat(TotpPolicy.mustSetupTwoFactor(Role.ADMIN_GIE, u)).isFalse();
    }
}
