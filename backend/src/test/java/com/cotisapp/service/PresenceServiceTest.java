package com.cotisapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceServiceTest {

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        presenceService = new PresenceService();
    }

    @Test
    void utilisateurEnLigneApresTouch() {
        presenceService.touch(10L, 2L);
        assertThat(presenceService.isOnline(10L, 2L)).isTrue();
    }

    @Test
    void utilisateurHorsLigneSansActivite() {
        assertThat(presenceService.isOnline(99L, 2L)).isFalse();
    }

    @Test
    void presenceParOrganisation() {
        presenceService.touch(10L, 2L);
        assertThat(presenceService.isOnline(10L, 3L)).isFalse();
    }
}
