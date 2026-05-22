package com.cotisapp.util;

import com.cotisapp.domain.enums.ModePaiement;

public final class ModePaiementHelper {

    private ModePaiementHelper() {}

    public static ModePaiement parser(String raw) {
        if (raw == null || raw.isBlank()) {
            return ModePaiement.ESPECES;
        }
        String n = raw.trim().toUpperCase();
        if ("MOBILE_MONEY".equals(n)) {
            return ModePaiement.WAVE;
        }
        try {
            return ModePaiement.valueOf(n);
        } catch (IllegalArgumentException e) {
            return ModePaiement.ESPECES;
        }
    }

    public static String libelle(ModePaiement mode) {
        if (mode == null) {
            return "Espèces";
        }
        return switch (mode) {
            case WAVE -> "Wave";
            case ORANGE_MONEY -> "Orange Money";
            case ESPECES -> "Espèces";
        };
    }

    public static String enrichirObservation(String base, ModePaiement mode, String referencePaiement) {
        String part = "Paiement: " + libelle(mode);
        if (referencePaiement != null && !referencePaiement.isBlank()) {
            part += " (réf. " + referencePaiement.trim() + ")";
        }
        if (base == null || base.isBlank()) {
            return part;
        }
        if (base.contains(part)) {
            return base;
        }
        return base + " · " + part;
    }
}
