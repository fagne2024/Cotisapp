package com.cotisapp.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PartsCotisationUtilTest {

  private static final BigDecimal MIN = new BigDecimal("1000");
  private static final BigDecimal MAX = new BigDecimal("10000");

  @Test
  void bornes_et_paliers() {
    assertThat(PartsCotisationUtil.calculerParts(new BigDecimal("500"), MIN, MAX, 1, 10)).isEqualTo(1);
    assertThat(PartsCotisationUtil.calculerParts(MIN, MIN, MAX, 1, 10)).isEqualTo(1);
    assertThat(PartsCotisationUtil.calculerParts(new BigDecimal("2000"), MIN, MAX, 1, 10)).isEqualTo(2);
    assertThat(PartsCotisationUtil.calculerParts(new BigDecimal("5500"), MIN, MAX, 1, 10)).isEqualTo(5);
    assertThat(PartsCotisationUtil.calculerParts(MAX, MIN, MAX, 1, 10)).isEqualTo(10);
    assertThat(PartsCotisationUtil.calculerParts(new BigDecimal("50000"), MIN, MAX, 1, 10)).isEqualTo(10);
  }
}
