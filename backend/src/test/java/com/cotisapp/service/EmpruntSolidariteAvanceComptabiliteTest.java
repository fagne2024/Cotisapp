package com.cotisapp.service;

import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.enums.TypeEmprunt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie la symétrie comptable : débit Caisse à l'octroi = même montant restitué à la Caisse au remboursement ;
 * part Solidarité remboursée = crédit Solidarité (hors apurement dette interne).
 */
class EmpruntSolidariteAvanceComptabiliteTest {

  @Test
  void repartition_rembourse_priorise_montant_debite_caisse_a_l_octroi() {
    Emprunt emprunt = Emprunt.builder()
        .typeEmprunt(TypeEmprunt.SOLIDARITE)
        .montantAvanceCaisse(new BigDecimal("50"))
        .montantRembourseAvanceCaisse(BigDecimal.ZERO)
        .build();

    var rep1 = EmpruntAvanceCaisseHelper.repartirRemboursement(emprunt, new BigDecimal("30"));
    assertThat(rep1.partCaisse()).isEqualByComparingTo("30");
    assertThat(rep1.partSolidarite()).isEqualByComparingTo("0");

    emprunt.setMontantRembourseAvanceCaisse(new BigDecimal("30"));
    var rep2 = EmpruntAvanceCaisseHelper.repartirRemboursement(emprunt, new BigDecimal("80"));
    assertThat(rep2.partCaisse()).isEqualByComparingTo("20");
    assertThat(rep2.partSolidarite()).isEqualByComparingTo("60");

    emprunt.setMontantRembourseAvanceCaisse(new BigDecimal("50"));
    var rep3 = EmpruntAvanceCaisseHelper.repartirRemboursement(emprunt, new BigDecimal("100"));
    assertThat(rep3.partCaisse()).isEqualByComparingTo("0");
    assertThat(rep3.partSolidarite()).isEqualByComparingTo("100");
  }

  @Test
  void avance_octroi_egale_part_caisse_max_remboursable() {
    BigDecimal soldeSol = new BigDecimal("100");
    BigDecimal debitTotal = new BigDecimal("150");
    BigDecimal avance = AccorderEmpruntService.calculerAvanceCaisseVersSolidarite(soldeSol, debitTotal);
    assertThat(avance).isEqualByComparingTo("50");
    assertThat(EmpruntAvanceCaisseHelper.debitSolidaritePropre(debitTotal, avance)).isEqualByComparingTo("100");
  }
}
