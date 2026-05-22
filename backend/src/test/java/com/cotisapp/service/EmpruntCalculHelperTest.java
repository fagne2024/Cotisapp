package com.cotisapp.service;



import com.cotisapp.domain.entity.RegleOperation;

import com.cotisapp.domain.enums.TypeModeCalcul;

import com.cotisapp.domain.enums.TypeOperation;

import com.cotisapp.exception.BusinessException;

import org.junit.jupiter.api.Test;



import java.math.BigDecimal;

import java.util.List;



import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;



class EmpruntCalculHelperTest {



    @Test

    void simuler_une_echeance_montant_egal_nominal_plus_frais() {

        RegleOperation regle = RegleOperation.builder()

                .typeOperation(TypeOperation.EMPRUNT)

                .typeFrais(TypeModeCalcul.FIXE)

                .montantFrais(new BigDecimal("5000"))

                .nbEcheancesMin(1)

                .nbEcheancesMax(1)

                .nbEcheancesDefaut(1)

                .build();



        EmpruntCalculHelper.SimulationEmprunt sim =

                EmpruntCalculHelper.simuler(new BigDecimal("100000"), regle, null);



        assertThat(sim.paiementUnique()).isTrue();

        assertThat(sim.nbEcheances()).isEqualTo(1);

        assertThat(sim.montantParEcheance()).isEqualByComparingTo("105000");

        assertThat(sim.totalRembourser()).isEqualByComparingTo("105000");

    }



    @Test

    void simuler_plusieurs_echeances_divise_le_total() {

        RegleOperation regle = RegleOperation.builder()

                .typeOperation(TypeOperation.EMPRUNT)

                .nbEcheancesMin(2)

                .nbEcheancesMax(12)

                .nbEcheancesDefaut(4)

                .build();



        EmpruntCalculHelper.SimulationEmprunt sim =

                EmpruntCalculHelper.simuler(new BigDecimal("100000"), regle, 4);



        assertThat(sim.paiementUnique()).isFalse();

        assertThat(sim.nbEcheances()).isEqualTo(4);

        assertThat(sim.montantParEcheance()).isEqualByComparingTo("25000");

        assertThat(sim.montantDerniereEcheance()).isEqualByComparingTo("25000");

    }



    @Test

    void repartir_respecte_max_et_reliquat_derniere_echeance() {

        List<BigDecimal> parts = EmpruntCalculHelper.repartirMontantsEcheances(

                new BigDecimal("105000"), 4, null, new BigDecimal("15000"));



        assertThat(parts).hasSize(4);

        assertThat(parts.get(0)).isEqualByComparingTo("15000");

        assertThat(parts.get(1)).isEqualByComparingTo("15000");

        assertThat(parts.get(2)).isEqualByComparingTo("15000");

        assertThat(parts.get(3)).isEqualByComparingTo("60000");

        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("105000");

    }

    @Test
    void repartir_emprunt_etale_reliquat_sur_derniere_echeance() {
        List<BigDecimal> parts = EmpruntCalculHelper.repartirMontantsEcheances(
                new BigDecimal("200000"), 7, new BigDecimal("5000"), new BigDecimal("30000"));

        assertThat(parts).hasSize(7);
        assertThat(parts.get(0)).isEqualByComparingTo("30000");
        assertThat(parts.get(5)).isEqualByComparingTo("30000");
        assertThat(parts.get(6)).isEqualByComparingTo("20000");
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("200000");
    }

    @Test

    void simuler_avec_max_echeance_derniere_plus_elevee() {

        RegleOperation regle = RegleOperation.builder()

                .typeOperation(TypeOperation.EMPRUNT)

                .nbEcheancesMin(3)

                .nbEcheancesMax(12)

                .nbEcheancesDefaut(4)

                .montantEcheanceMax(new BigDecimal("15000"))

                .build();



        EmpruntCalculHelper.SimulationEmprunt sim =

                EmpruntCalculHelper.simuler(new BigDecimal("100000"), regle, 4);



        assertThat(sim.montantParEcheance()).isEqualByComparingTo("15000");

        assertThat(sim.montantDerniereEcheance()).isEqualByComparingTo("55000");

    }



    @Test
    void calculerFrais_sans_type_retourne_zero() {
        RegleOperation regle = RegleOperation.builder()
                .typeOperation(TypeOperation.EMPRUNT)
                .montantFrais(new BigDecimal("5000"))
                .pourcentageFrais(new BigDecimal("5"))
                .build();

        assertThat(EmpruntCalculHelper.calculerFrais(new BigDecimal("100000"), regle))
                .isEqualByComparingTo("0");
    }

    @Test
    void validerMontantEcheance_reliquat_derniere_peut_etre_inferieur_au_min() {
        RegleOperation regle = RegleOperation.builder()
                .montantEcheanceMin(new BigDecimal("30000"))
                .montantEcheanceMax(new BigDecimal("200000"))
                .build();
        List<BigDecimal> montants = List.of(
                new BigDecimal("30000"),
                new BigDecimal("30000"),
                new BigDecimal("30000"),
                new BigDecimal("30000"),
                new BigDecimal("30000"),
                new BigDecimal("30000"),
                new BigDecimal("20000"));
        EmpruntCalculHelper.SimulationEmprunt sim = new EmpruntCalculHelper.SimulationEmprunt(
                new BigDecimal("200000"),
                BigDecimal.ZERO,
                new BigDecimal("200000"),
                7,
                new BigDecimal("30000"),
                new BigDecimal("20000"),
                montants,
                false);

        EmpruntCalculHelper.validerMontantEcheance(sim, regle);
    }

    @Test

    void validerMontantEcheance_paiement_unique_controle_le_total() {

        RegleOperation regle = RegleOperation.builder()

                .montantEcheanceMin(new BigDecimal("100000"))

                .montantEcheanceMax(new BigDecimal("200000"))

                .build();

        EmpruntCalculHelper.SimulationEmprunt sim = new EmpruntCalculHelper.SimulationEmprunt(

                new BigDecimal("50000"),

                new BigDecimal("5000"),

                new BigDecimal("55000"),

                1,

                new BigDecimal("55000"),

                new BigDecimal("55000"),

                List.of(new BigDecimal("55000")),

                true);



        assertThatThrownBy(() -> EmpruntCalculHelper.validerMontantEcheance(sim, regle))

                .isInstanceOf(BusinessException.class)

                .hasMessageContaining("paiement unique");

    }

}


