package com.cotisapp.service;

import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.enums.TypeEmprunt;

import java.math.BigDecimal;

/**
 * Avance Caisse → Solidarité lors d'un octroi solidarité : suivi et répartition des remboursements.
 */
public final class EmpruntAvanceCaisseHelper {

    public static final String PREFIX_AVANCE_OCTROI = "[AVANCE_CAISSE]";
    public static final String PREFIX_REMBOURSEMENT_SPLIT = "[REMBOURSEMENT_SPLIT]";

    private EmpruntAvanceCaisseHelper() {}

    public record RepartitionRemboursement(BigDecimal partCaisse, BigDecimal partSolidarite) {}

    public static BigDecimal avanceCaisseRestant(Emprunt emprunt) {
        if (emprunt == null || emprunt.getTypeEmprunt() != TypeEmprunt.SOLIDARITE) {
            return BigDecimal.ZERO;
        }
        BigDecimal avance = nullToZero(emprunt.getMontantAvanceCaisse());
        BigDecimal remb = nullToZero(emprunt.getMontantRembourseAvanceCaisse());
        return avance.subtract(remb).max(BigDecimal.ZERO);
    }

    /** Montant débité sur le fonds Solidarité à l'octroi (hors avance Caisse). */
    public static BigDecimal debitSolidaritePropre(BigDecimal debitTotal, BigDecimal avanceCaisse) {
        return nullToZero(debitTotal).subtract(nullToZero(avanceCaisse)).max(BigDecimal.ZERO);
    }

    public static RepartitionRemboursement repartirRemboursement(Emprunt emprunt, BigDecimal montantPaiement) {
        BigDecimal montant = nullToZero(montantPaiement);
        if (emprunt == null || emprunt.getTypeEmprunt() != TypeEmprunt.SOLIDARITE) {
            return new RepartitionRemboursement(BigDecimal.ZERO, montant);
        }
        BigDecimal restantAvance = avanceCaisseRestant(emprunt);
        BigDecimal partCaisse = montant.min(restantAvance);
        BigDecimal partSolidarite = montant.subtract(partCaisse);
        return new RepartitionRemboursement(partCaisse, partSolidarite);
    }

    public static String observationOctroiAvance(BigDecimal avanceCaisse) {
        if (avanceCaisse == null || avanceCaisse.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return PREFIX_AVANCE_OCTROI + " "
                + avanceCaisse.stripTrailingZeros().toPlainString()
                + " F complétés depuis la Caisse (solde Solidarité insuffisant)";
    }

    public static String observationRemboursementSplit(BigDecimal partCaisse, BigDecimal partSolidarite) {
        if (partCaisse.compareTo(BigDecimal.ZERO) <= 0 && partSolidarite.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return PREFIX_REMBOURSEMENT_SPLIT + " Caisse (avance): "
                + partCaisse.stripTrailingZeros().toPlainString()
                + " F | Solidarité: "
                + partSolidarite.stripTrailingZeros().toPlainString()
                + " F";
    }

    public static BigDecimal extrairePartCaisseRemboursement(String observation) {
        if (observation == null || !observation.contains(PREFIX_REMBOURSEMENT_SPLIT)) {
            return BigDecimal.ZERO;
        }
        try {
            int start = observation.indexOf("Caisse (avance):");
            if (start < 0) {
                return BigDecimal.ZERO;
            }
            start += "Caisse (avance):".length();
            int end = observation.indexOf("F |", start);
            if (end < 0) {
                end = observation.indexOf('|', start);
            }
            if (end < 0) {
                return BigDecimal.ZERO;
            }
            String num = observation.substring(start, end).trim().replace(',', '.');
            return new BigDecimal(num).max(BigDecimal.ZERO);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public static String fusionnerObservation(String existante, String ajout) {
        if (ajout == null || ajout.isBlank()) {
            return existante;
        }
        if (existante == null || existante.isBlank()) {
            return ajout;
        }
        return existante + " — " + ajout;
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
