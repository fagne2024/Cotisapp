package com.cotisapp.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JournalModificationFormatterTest {

    @Test
    void champModifie_ignoreSiIdentique() {
        assertThat(JournalModificationFormatter.champModifie("Nom", "Dupont", "Dupont")).isNull();
    }

    @Test
    void resumeModifications_jointLesChangements() {
        List<String> changes = new ArrayList<>();
        JournalModificationFormatter.ajouterSiChange(changes, "Email", "a@x.sn", "b@x.sn");
        String resume = JournalModificationFormatter.resumeModifications("Profil ARAME SENE", changes);
        assertThat(resume).contains("Modifications").contains("Email").contains("a@x.sn").contains("b@x.sn");
    }
}
