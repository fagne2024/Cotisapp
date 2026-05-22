package com.cotisapp.util;

public final class TelephoneUtil {

    private TelephoneUtil() {}

    public static String normaliser(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 9 && (digits.startsWith("7") || digits.startsWith("3"))) {
            digits = "221" + digits;
        }
        return digits;
    }

    public static boolean estEmail(String identifiant) {
        return identifiant != null && identifiant.contains("@");
    }
}
