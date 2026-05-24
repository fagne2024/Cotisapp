package com.cotisapp.service;

import com.cotisapp.domain.entity.Echeance;
import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.enums.TypeModeCalcul;
import com.cotisapp.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class EmpruntCalculHelper {

    private EmpruntCalculHelper() {}

    public record SimulationEmprunt(
            BigDecimal capital,
            BigDecimal frais,
            BigDecimal totalRembourser,
            int nbEcheances,
            BigDecimal montantParEcheance,
            BigDecimal montantDerniereEcheance,
            List<BigDecimal> montantsEcheances,
            boolean paiementUnique) {}

  /** Pénalité unitaire (fixe ou % de la base) pour une échéance en retard. */
    public static BigDecimal calculerPenaliteUnitaire(BigDecimal base, RegleOperation regle) {
        if (regle == null || base == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal m = base.max(BigDecimal.ZERO);
        if (regle.getTypePenalite() == TypeModeCalcul.POURCENTAGE && regle.getPourcentagePenalite() != null) {
            return m.multiply(regle.getPourcentagePenalite())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        }
        if (regle.getTypePenalite() == TypeModeCalcul.FIXE && regle.getMontantPenalite() != null) {
            return regle.getMontantPenalite().setScale(0, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /** Mois de retard (au moins 1 dès le lendemain de l'échéance). */
    public static int moisEnRetard(LocalDate dateEcheance, LocalDate dateReference) {
        if (dateEcheance == null || dateReference == null || !dateReference.isAfter(dateEcheance)) {
            return 0;
        }
        long jours = ChronoUnit.DAYS.between(dateEcheance, dateReference);
        return (int) Math.max(1, (jours + 29) / 30);
    }

    /** Part nominale (capital) d'une échéance caisse, hors frais d'octroi. */
    public static BigDecimal capitalNominalEcheance(Emprunt emprunt, Echeance ech) {
        BigDecimal total = emprunt.getMontantTotal();
        BigDecimal fraisTotal = emprunt.getMontantFrais() != null ? emprunt.getMontantFrais() : BigDecimal.ZERO;
        BigDecimal capitalTotal = total.subtract(fraisTotal).max(BigDecimal.ZERO);
        if (total.compareTo(BigDecimal.ZERO) <= 0 || ech.getMontantEcheance() == null) {
            return BigDecimal.ZERO;
        }
        return ech.getMontantEcheance()
                .multiply(capitalTotal)
                .divide(total, 0, RoundingMode.HALF_UP);
    }

    public static BigDecimal fraisPartEcheance(Emprunt emprunt, Echeance ech) {
        if (ech.getMontantEcheance() == null) {
            return BigDecimal.ZERO;
        }
        return ech.getMontantEcheance().subtract(capitalNominalEcheance(emprunt, ech)).max(BigDecimal.ZERO);
    }

    /** Nominal restant à rembourser sur l'échéance (après paiements partiels, répartition proportionnelle). */
    public static BigDecimal capitalNominalRestant(Emprunt emprunt, Echeance ech) {
        BigDecimal nominalBrut = capitalNominalEcheance(emprunt, ech);
        BigDecimal restEch = ech.getMontantEcheance().subtract(ech.getMontantPaye()).max(BigDecimal.ZERO);
        if (ech.getMontantEcheance().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (restEch.compareTo(ech.getMontantEcheance()) >= 0) {
            return nominalBrut;
        }
        return nominalBrut
                .multiply(restEch)
                .divide(ech.getMontantEcheance(), 0, RoundingMode.HALF_UP);
    }

    public static BigDecimal fraisPartRestant(Emprunt emprunt, Echeance ech) {
        BigDecimal restEch = ech.getMontantEcheance().subtract(ech.getMontantPaye()).max(BigDecimal.ZERO);
        return restEch.subtract(capitalNominalRestant(emprunt, ech)).max(BigDecimal.ZERO);
    }

    /** Capital nominal encore dû sur un emprunt (hors frais), pour le plafond cumulé à l'octroi. */
    public static BigDecimal capitalRestantEmprunt(Emprunt emprunt) {
        if (emprunt == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalEmprunt = nz(emprunt.getMontantTotal());
        BigDecimal fraisTotal = nz(emprunt.getMontantFrais());
        BigDecimal capitalTotal = totalEmprunt.subtract(fraisTotal).max(BigDecimal.ZERO);
        BigDecimal rembourse = nz(emprunt.getMontantRembourse());
        BigDecimal fraisRembourse = fraisTotal.min(rembourse.subtract(capitalTotal).max(BigDecimal.ZERO));
        BigDecimal capitalRembourse = capitalTotal.min(rembourse.subtract(fraisRembourse).max(BigDecimal.ZERO));
        return capitalTotal.subtract(capitalRembourse).max(BigDecimal.ZERO);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * Part intérêts / frais dans un paiement sur une échéance (le reste du paiement est affecté au nominal).
     */
    public static BigDecimal fraisPortionPaiementEcheance(Emprunt emprunt, Echeance ech, BigDecimal montantPaiement) {
        if (montantPaiement == null || montantPaiement.signum() <= 0 || ech == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal restCap = capitalNominalRestant(emprunt, ech);
        BigDecimal restFrais = fraisPartRestant(emprunt, ech);
        BigDecimal payCap = montantPaiement.min(restCap);
        BigDecimal reste = montantPaiement.subtract(payCap);
        return reste.min(restFrais).max(BigDecimal.ZERO);
    }

    public static BigDecimal calculerPenaliteRetard(
            BigDecimal baseEcheance,
            RegleOperation regle,
            LocalDate dateEcheance,
            LocalDate datePaiement) {
        int mois = moisEnRetard(dateEcheance, datePaiement);
        if (mois <= 0) {
            return BigDecimal.ZERO;
        }
        return calculerPenaliteUnitaire(baseEcheance, regle).multiply(BigDecimal.valueOf(mois));
    }

    public static BigDecimal calculerFrais(BigDecimal capital, RegleOperation regle) {
        if (regle == null || capital == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal m = capital.max(BigDecimal.ZERO);
        if (regle.getTypeFrais() == TypeModeCalcul.POURCENTAGE && regle.getPourcentageFrais() != null) {
            return m.multiply(regle.getPourcentageFrais())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if (regle.getTypeFrais() == TypeModeCalcul.FIXE && regle.getMontantFrais() != null) {
            return regle.getMontantFrais();
        }
        return BigDecimal.ZERO;
    }

    public static SimulationEmprunt simuler(BigDecimal capital, RegleOperation regle, Integer nbEcheancesSaisi) {
        BigDecimal m = capital.max(BigDecimal.ZERO);
        BigDecimal frais = calculerFrais(m, regle);
        int nbMin = regle != null && regle.getNbEcheancesMin() != null ? regle.getNbEcheancesMin() : 1;
        int nbMax = regle != null && regle.getNbEcheancesMax() != null ? regle.getNbEcheancesMax() : 24;
        int nbDef = regle != null && regle.getNbEcheancesDefaut() != null ? regle.getNbEcheancesDefaut() : nbMin;
        int nb = nbEcheancesSaisi != null && nbEcheancesSaisi > 0 ? nbEcheancesSaisi : nbDef;
        nb = Math.min(nbMax, Math.max(nbMin, nb));
        BigDecimal total = m.add(frais);
        boolean paiementUnique = nb == 1;

        BigDecimal minEch = regle != null ? regle.getMontantEcheanceMin() : null;
        BigDecimal maxEch = regle != null ? regle.getMontantEcheanceMax() : null;
        List<BigDecimal> montantsEcheances = paiementUnique
                ? List.of(total)
                : repartirMontantsEcheances(total, nb, minEch, maxEch);
        BigDecimal parEcheance = montantsEcheances.isEmpty() ? total : montantsEcheances.get(0);
        BigDecimal derniereEcheance = montantsEcheances.isEmpty()
                ? total
                : montantsEcheances.get(montantsEcheances.size() - 1);

        return new SimulationEmprunt(
                m, frais, total, nb, parEcheance, derniereEcheance, montantsEcheances, paiementUnique);
    }

    /**
     * Les (n−1) premières échéances respectent le plafond max ; la dernière reçoit le reliquat.
     */
    public static List<BigDecimal> repartirMontantsEcheances(
            BigDecimal total, int nb, BigDecimal montantMin, BigDecimal montantMax) {
        BigDecimal t = total.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
        if (nb <= 0) {
            return List.of();
        }
        if (nb == 1) {
            return List.of(t);
        }
        BigDecimal minE = montantMin != null ? montantMin.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal maxE = montantMax != null ? montantMax.max(BigDecimal.ZERO) : null;

        List<BigDecimal> montants = new ArrayList<>();
        BigDecimal reste = t;
        for (int i = 0; i < nb - 1; i++) {
            int slotsApres = nb - i - 1;
            BigDecimal part;
            if (maxE != null) {
                BigDecimal reserveMin = minE.multiply(BigDecimal.valueOf(slotsApres));
                BigDecimal maxAutorise = reste.subtract(reserveMin).max(BigDecimal.ZERO);
                part = maxE.min(maxAutorise);
            } else {
                part = reste.divide(BigDecimal.valueOf(slotsApres + 1L), 0, RoundingMode.CEILING);
            }
            if (minE.compareTo(BigDecimal.ZERO) > 0) {
                part = part.max(minE.min(reste));
            }
            part = part.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
            montants.add(part);
            reste = reste.subtract(part);
        }
        montants.add(reste.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP));
        return montants;
    }

    public static void validerMontant(BigDecimal montant, RegleOperation regle) {
        if (regle.getMontantMin() != null && montant.compareTo(regle.getMontantMin()) < 0) {
            throw new BusinessException("Montant inférieur au minimum autorisé: " + regle.getMontantMin());
        }
        if (regle.getMontantMax() != null && montant.compareTo(regle.getMontantMax()) > 0) {
            throw new BusinessException("Montant supérieur au maximum autorisé: " + regle.getMontantMax());
        }
    }

    /**
     * Si 1 échéance, min/max portent sur le total nominal + frais.
     * Sinon min/max s'appliquent aux échéances 1..n−1 ; la dernière porte le reliquat (sans min).
     */
    public static void validerMontantEcheance(SimulationEmprunt sim, RegleOperation regle) {
        if (regle == null || sim == null) {
            return;
        }
        if (sim.paiementUnique()) {
            validerUneEcheance(sim.totalRembourser(), regle, "paiement unique (nominal + frais)");
            return;
        }
        List<BigDecimal> montants = sim.montantsEcheances();
        if (montants == null || montants.isEmpty()) {
            validerUneEcheance(sim.montantParEcheance(), regle, "montant par échéance");
            return;
        }
        for (int i = 0; i < montants.size() - 1; i++) {
            validerUneEcheance(montants.get(i), regle, "montant par échéance (hors dernière)");
        }
    }

    private static void validerUneEcheance(BigDecimal montant, RegleOperation regle, String cible) {
        if (regle.getMontantEcheanceMin() != null && montant.compareTo(regle.getMontantEcheanceMin()) < 0) {
            throw new BusinessException(String.format(
                    "%s inférieur au minimum (%s < %s)", cible, montant, regle.getMontantEcheanceMin()));
        }
        if (regle.getMontantEcheanceMax() != null && montant.compareTo(regle.getMontantEcheanceMax()) > 0) {
            throw new BusinessException(String.format(
                    "%s supérieur au maximum (%s > %s)", cible, montant, regle.getMontantEcheanceMax()));
        }
    }
}
