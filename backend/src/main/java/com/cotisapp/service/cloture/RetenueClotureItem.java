package com.cotisapp.service.cloture;

import com.cotisapp.domain.enums.TypeModeCalcul;

import java.math.BigDecimal;

public record RetenueClotureItem(String libelle, TypeModeCalcul typeMode, BigDecimal valeur, int ordre) {}
