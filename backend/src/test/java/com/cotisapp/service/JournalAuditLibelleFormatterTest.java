package com.cotisapp.service;

import com.cotisapp.domain.entity.JournalAudit;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.domain.enums.TypeEvenementJournal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JournalAuditLibelleFormatterTest {

    @Test
    void visiteModule_libelleExplicite() {
        JournalAudit j = JournalAudit.builder()
                .typeEvenement(TypeEvenementJournal.MODULE_VISITE)
                .utilisateurNom("ARAME SENE")
                .role(Role.ADMIN_GIE)
                .moduleLibelle("Membres")
                .routePath("/organisations/2/membres")
                .action("MODULE_VISITE")
                .succes(true)
                .build();

        assertThat(JournalAuditLibelleFormatter.titre(j)).contains("Membres");
        assertThat(JournalAuditLibelleFormatter.detailAffichage(j))
                .contains("ARAME SENE")
                .contains("/organisations/2/membres");
    }

    @Test
    void actionMetier_operationNumerotee() {
        assertThat(JournalAuditLibelleFormatter.enrichirDetailsAction("COTISATION", "Opération 137"))
                .contains("Cotisation hebdomadaire")
                .contains("137");
    }

    @Test
    void resumeNavigateur_chromeWindows() {
        String ua =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";
        assertThat(JournalAuditLibelleFormatter.resumeNavigateur(ua)).contains("Chrome").contains("Windows");
    }

    @Test
    void detailAffichage_conserveModificationsExplicites() {
        var j = JournalAudit.builder()
                .typeEvenement(TypeEvenementJournal.ACTION_METIER)
                .action("MEMBRE_MAJ")
                .utilisateurNom("Admin Test")
                .details("GIE-001 — Dupont — Modifications : Nom : Martin → Dupont")
                .build();
        assertThat(JournalAuditLibelleFormatter.detailAffichage(j))
                .contains("Modifications")
                .contains("Martin → Dupont");
    }
}
