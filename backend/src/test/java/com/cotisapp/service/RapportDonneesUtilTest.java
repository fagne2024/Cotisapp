package com.cotisapp.service;

import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.entity.SuiviMensuel;
import com.cotisapp.domain.enums.StatutSuiviMensuel;
import com.cotisapp.domain.enums.TypeOperation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RapportDonneesUtilTest {

    @Test
    void operationComptable_exclutAnnulationEtContrePassation() {
        Operation ok = Operation.builder().annulee(false).build();
        Operation annulee = Operation.builder().annulee(true).build();
        Operation contrePassation = Operation.builder().annulee(false).operationOrigineId(99L).build();

        assertThat(RapportDonneesHelper.operationComptable(ok)).isTrue();
        assertThat(RapportDonneesHelper.operationComptable(annulee)).isFalse();
        assertThat(RapportDonneesHelper.operationComptable(contrePassation)).isFalse();
    }

    @Test
    void cleSemaineCotisation_utiliseDateSiPasMarqueurIso() {
        Operation annulation = Operation.builder()
                .typeOperation(TypeOperation.COTISATION)
                .dateOperation(LocalDate.of(2026, 5, 10))
                .observation("[ANNULATION] Contre-passation opération #137")
                .build();
        assertThat(RapportDonneesHelper.cleSemaineCotisation(annulation)).isEqualTo("2026-W19");

        Operation normale = Operation.builder()
                .typeOperation(TypeOperation.COTISATION)
                .dateOperation(LocalDate.of(2026, 5, 10))
                .observation("Paiement · [2026-W20]")
                .build();
        assertThat(RapportDonneesHelper.cleSemaineCotisation(normale)).isEqualTo("2026-W20");
    }

    @Test
    void participation_refleteProgressionPartielle() {
        List<Operation> ops = List.of(
                Operation.builder()
                        .typeOperation(TypeOperation.COTISATION)
                        .observation("[2026-W20]")
                        .build());
        SuiviMensuel suivi = SuiviMensuel.builder()
                .montantDu(new BigDecimal("25000"))
                .montantPaye(BigDecimal.ZERO)
                .statut(StatutSuiviMensuel.NON_PAYE)
                .build();
        BigDecimal regleMois = new BigDecimal("25000");

        assertThat(RapportDonneesHelper.ratioHebdomadaire(1, 4)).isEqualTo(0.25);
        assertThat(RapportDonneesHelper.pctParticipationMoyenne(1, 4, suivi, ops, "2026-05", regleMois))
                .isEqualTo(13);
        assertThat(RapportDonneesHelper.membreAJour(1, 4, suivi, ops, "2026-05", regleMois)).isFalse();
        assertThat(RapportDonneesHelper.membreMoisContribue(suivi, ops, "2026-05", regleMois)).isFalse();
    }

    @Test
    void cotisationMois_compteUneFoisParMois() {
        SuiviMensuel suivi = SuiviMensuel.builder()
                .montantDu(new BigDecimal("25000"))
                .montantPaye(new BigDecimal("25000"))
                .statut(StatutSuiviMensuel.PAYE)
                .build();
        List<Operation> ops = List.of(
                Operation.builder()
                        .typeOperation(TypeOperation.COTISATION_MOIS)
                        .moisAnnee("2026-05")
                        .montant(new BigDecimal("25000"))
                        .build());

        assertThat(RapportDonneesHelper.membreMoisAJour(suivi, ops, "2026-05", new BigDecimal("25000")))
                .isTrue();
        assertThat(RapportDonneesHelper.compterSemainesAttendues(
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 20), 4))
                .isEqualTo(4);
    }
}
