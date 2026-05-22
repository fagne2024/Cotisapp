package com.cotisapp.util;

import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cotisations par parts : montant = nombre de parts × valeur d'une part.
 */
public final class PartsCotisationUtil {

    private PartsCotisationUtil() {}

    public static boolean modePartsActif(RegleOperation regle) {
        return regle != null
                && (regle.getTypeOperation() == TypeOperation.COTISATION
                        || regle.getTypeOperation() == TypeOperation.COTISATION_MOIS)
                && regle.getMontantParPart() != null
                && regle.getMontantParPart().signum() > 0
                && regle.getPartsMin() != null
                && regle.getPartsMax() != null
                && regle.getPartsMax() >= regle.getPartsMin();
    }

    public static BigDecimal montantDepuisParts(int nbParts, BigDecimal montantParPart) {
        return montantParPart.multiply(BigDecimal.valueOf(nbParts));
    }

    /**
     * Nombre de parts pour la répartition clôture : montant moyen / montant min (arrondi inférieur),
     * borné entre {@code partsMin} et {@code partsMax}.
     */
    public static int calculerParts(
            BigDecimal montant,
            BigDecimal montantMin,
            BigDecimal montantMax,
            Integer partsMin,
            Integer partsMax) {
        if (montant == null || montant.signum() <= 0 || montantMin == null || montantMin.signum() <= 0) {
            return 0;
        }
        int pMin = partsMin != null && partsMin > 0 ? partsMin : 1;
        int pMax = partsMax != null && partsMax >= pMin ? partsMax : pMin;
        BigDecimal base = montant;
        if (montantMax != null && montantMax.signum() > 0 && base.compareTo(montantMax) > 0) {
            base = montantMax;
        }
        int parts = base.divide(montantMin, 0, RoundingMode.DOWN).intValue();
        if (parts < pMin) {
            parts = pMin;
        }
        if (parts > pMax) {
            parts = pMax;
        }
        return parts;
    }

    public static int nombrePartsDepuisMontant(BigDecimal montant, BigDecimal montantParPart) {
        if (montant == null || montantParPart == null || montantParPart.signum() <= 0) {
            return 0;
        }
        BigDecimal[] div = montant.divideAndRemainder(montantParPart);
        if (div[1].compareTo(BigDecimal.ZERO) != 0) {
            return -1;
        }
        try {
            return div[0].intValueExact();
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    public static void synchroniserMontantsDepuisParts(RegleOperation regle) {
        if (!modePartsActif(regle)) {
            return;
        }
        regle.setMontantMin(montantDepuisParts(regle.getPartsMin(), regle.getMontantParPart()));
        regle.setMontantMax(montantDepuisParts(regle.getPartsMax(), regle.getMontantParPart()));
    }

    /**
     * Aligne parts min/max sur les montants min/max lorsque ceux-ci sont des multiples de la valeur d'une part
     * (corrige les données incohérentes, ex. 1 000–10 000 F avec 25 parts).
     */
    public static void normaliserPartsDepuisMontants(RegleOperation regle) {
        if (regle == null
                || (regle.getTypeOperation() != TypeOperation.COTISATION
                        && regle.getTypeOperation() != TypeOperation.COTISATION_MOIS)) {
            return;
        }
        BigDecimal vpp = regle.getMontantParPart();
        if (vpp == null || vpp.signum() <= 0) {
            return;
        }
        BigDecimal mMin = regle.getMontantMin();
        BigDecimal mMax = regle.getMontantMax();
        if (mMin == null || mMax == null || mMin.signum() <= 0 || mMax.compareTo(mMin) < 0) {
            return;
        }
        int pMin = nombrePartsDepuisMontant(mMin, vpp);
        int pMax = nombrePartsDepuisMontant(mMax, vpp);
        if (pMin < 1 || pMax < pMin) {
            return;
        }
        regle.setPartsMin(pMin);
        regle.setPartsMax(pMax);
        synchroniserMontantsDepuisParts(regle);
    }

    public static void validerMontantCotisation(BigDecimal montant, RegleOperation regle) {
        if (montant == null || montant.signum() <= 0) {
            throw new BusinessException("Montant de cotisation invalide");
        }
        if (modePartsActif(regle)) {
            int parts = nombrePartsDepuisMontant(montant, regle.getMontantParPart());
            if (parts < 0) {
                throw new BusinessException(
                        "Le montant doit être un multiple de "
                                + regle.getMontantParPart().stripTrailingZeros().toPlainString()
                                + " F (valeur d'une part)");
            }
            if (parts < regle.getPartsMin() || parts > regle.getPartsMax()) {
                throw new BusinessException(
                        "Nombre de parts invalide : "
                                + regle.getPartsMin()
                                + " à "
                                + regle.getPartsMax()
                                + " parts (1 part = "
                                + regle.getMontantParPart().stripTrailingZeros().toPlainString()
                                + " F)");
            }
            return;
        }
        if (regle.getMontantMin() != null && montant.compareTo(regle.getMontantMin()) < 0) {
            throw new BusinessException("Montant inférieur au minimum: " + regle.getMontantMin());
        }
        if (regle.getMontantMax() != null && montant.compareTo(regle.getMontantMax()) > 0) {
            throw new BusinessException("Montant supérieur au maximum: " + regle.getMontantMax());
        }
    }
}