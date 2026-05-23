package com.cotisapp.service;

import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.ModePaiement;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.domain.enums.TypeOperation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JournalCaisseLibelleFormatterTest {

    @Test
    void remplaceEmpruntIdParMembreEtType() {
        Membre membre = Membre.builder()
                .id(5L)
                .codeMembre("M015")
                .nom("Kouassi")
                .prenom("Jean")
                .build();
        Emprunt emprunt = Emprunt.builder()
                .id(15L)
                .membreId(5L)
                .typeEmprunt(TypeEmprunt.ETALE)
                .build();
        var ctx = new JournalCaisseLibelleFormatter.Context(Map.of(5L, membre), Map.of(15L, emprunt));

        Operation op = Operation.builder()
                .typeOperation(TypeOperation.REMBOURSEMENT)
                .membreId(5L)
                .empruntId(15L)
                .modePaiement(ModePaiement.ESPECES)
                .observation(
                        "Paiement: Espèces · Frais / intérêts emprunt #15 → compte intérêts")
                .build();

        String libelle = JournalCaisseLibelleFormatter.format(op, ctx);

        assertThat(libelle).contains("Kouassi Jean (M015)");
        assertThat(libelle).contains("emprunt étalé");
        assertThat(libelle).doesNotContain("#15");
        assertThat(libelle).contains("Espèces");
        assertThat(libelle).contains("Frais et intérêts");
    }

    @Test
    void formateRepartitionRemboursementSolidarite() {
        Operation op = Operation.builder()
                .typeOperation(TypeOperation.REMBOURSEMENT)
                .membreId(2L)
                .modePaiement(ModePaiement.ESPECES)
                .observation(
                        "Paiement: Espèces · "
                                + EmpruntAvanceCaisseHelper.PREFIX_REMBOURSEMENT_SPLIT
                                + " Caisse (avance): 800 F | Solidarité: 19200 F")
                .build();
        Membre m =
                Membre.builder().id(2L).nom("Amen").prenom("Koffi").codeMembre("M003").build();
        var ctx = new JournalCaisseLibelleFormatter.Context(Map.of(2L, m), Map.of());

        String libelle = JournalCaisseLibelleFormatter.format(op, ctx);

        assertThat(libelle).contains("Remboursement — Amen Koffi (M003)");
        assertThat(libelle).contains("Répartition");
        assertThat(libelle).contains("800");
        assertThat(libelle).contains("19200");
        assertThat(libelle).doesNotContain("[REMBOURSEMENT_SPLIT]");
    }

    @Test
    void formateAnnulationAvecReference() {
        Operation op = Operation.builder()
                .typeOperation(TypeOperation.REMBOURSEMENT)
                .membreId(1L)
                .observation("[ANNULATION] Contre-passation opération #137 — Paiement: Espèces")
                .build();
        Membre m = Membre.builder().id(1L).nom("Test").prenom("User").codeMembre("M001").build();
        var ctx = new JournalCaisseLibelleFormatter.Context(Map.of(1L, m), Map.of());

        String libelle = JournalCaisseLibelleFormatter.format(op, ctx);

        assertThat(libelle).contains("Annulation");
        assertThat(libelle).contains("#137");
        assertThat(libelle).contains("Test User (M001)");
    }
}
