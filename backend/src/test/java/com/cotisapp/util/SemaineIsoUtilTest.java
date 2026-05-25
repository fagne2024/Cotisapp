package com.cotisapp.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SemaineIsoUtilTest {

    @Test
    void semaine22_2026_est_lundi_au_dimanche() {
        SemaineIsoUtil.BornesSemaine b = SemaineIsoUtil.parserSemaineKey("2026-W22");
        assertThat(b.lundi()).isEqualTo(LocalDate.of(2026, 5, 25));
        assertThat(b.lundi().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(b.dimanche()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(b.dimanche().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(SemaineIsoUtil.libelleSemaine("2026-W22")).isEqualTo("Semaine 22 — du 25 mai au 31 mai");
    }
}
