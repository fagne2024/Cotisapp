package com.cotisapp.service;

import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.enums.TypeEmprunt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EmpruntAvanceCaisseHelperTest {

    @Test
    void repartirRemboursement_priorise_remboursement_avance_caisse() {
        Emprunt emprunt = Emprunt.builder()
                .typeEmprunt(TypeEmprunt.SOLIDARITE)
                .montantAvanceCaisse(new BigDecimal("5000"))
                .montantRembourseAvanceCaisse(new BigDecimal("2000"))
                .build();

        var rep = EmpruntAvanceCaisseHelper.repartirRemboursement(emprunt, new BigDecimal("4000"));
        assertThat(rep.partCaisse()).isEqualByComparingTo("3000");
        assertThat(rep.partSolidarite()).isEqualByComparingTo("1000");
    }

    @Test
    void extrairePartCaisseRemboursement_parse_observation() {
        String obs = "[REMBOURSEMENT_SPLIT] Caisse (avance): 3000 F | Solidarité: 1000 F";
        assertThat(EmpruntAvanceCaisseHelper.extrairePartCaisseRemboursement(obs))
                .isEqualByComparingTo("3000");
    }
}
