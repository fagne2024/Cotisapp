package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.MouvementCompte;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.entity.SuiviMensuel;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.StatutSuiviMensuel;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.util.SemaineIsoUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Calculs communs rapports organisation / membre (données réelles, hors annulations). */
public final class RapportDonneesHelper {

    private static final Pattern SEMAINE_ISO_OBS =
            Pattern.compile("\\[(\\d{4}-W\\d{2})\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEMAINE_ISO_KEY = Pattern.compile("^(\\d{4})-W(\\d{1,2})$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter MOIS_ANNEE = DateTimeFormatter.ofPattern("yyyy-MM");

    private RapportDonneesHelper() {}

    public static boolean operationComptable(Operation o) {
        if (Boolean.TRUE.equals(o.getAnnulee())) {
            return false;
        }
        return o.getOperationOrigineId() == null;
    }

    /** Semaines ISO calendaires dans la période (repli si aucun PLANAD). */
    public static int compterSemainesAttendues(LocalDate debut, LocalDate fin, LocalDate today) {
        return compterSemainesAttendues(debut, fin, today, 0);
    }

    /**
     * Nombre de cotisations hebdo attendues : PLANAD tenus dans la période si renseigné,
     * sinon semaines ISO calendaires.
     */
    public static int compterSemainesAttendues(
            LocalDate debut, LocalDate fin, LocalDate today, int nbPlanadsDansPeriode) {
        if (nbPlanadsDansPeriode > 0) {
            return nbPlanadsDansPeriode;
        }
        LocalDate finEffective = fin.isBefore(today) ? fin : today;
        if (finEffective.isBefore(debut)) {
            return 0;
        }
        Set<String> semaines = new HashSet<>();
        WeekFields wf = WeekFields.ISO;
        for (LocalDate d = debut; !d.isAfter(finEffective); d = d.plusDays(1)) {
            int y = d.get(wf.weekBasedYear());
            int w = d.get(wf.weekOfWeekBasedYear());
            semaines.add(String.format(Locale.ROOT, "%d-W%02d", y, w));
        }
        return semaines.size();
    }

    public static long compterSemainesHebdoPayees(List<Operation> ops) {
        return ops.stream()
                .filter(o -> o.getTypeOperation() == TypeOperation.COTISATION)
                .filter(RapportDonneesHelper::operationComptable)
                .map(RapportDonneesHelper::cleSemaineCotisation)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    public static boolean cotisationMensuelleDue(SuiviMensuel suivi, BigDecimal montantRegleMois) {
        if (suivi != null
                && suivi.getMontantDu() != null
                && suivi.getMontantDu().compareTo(BigDecimal.ZERO) > 0) {
            return true;
        }
        return montantRegleMois != null && montantRegleMois.compareTo(BigDecimal.ZERO) > 0;
    }

    public static List<Operation> cotisationsMoisDuMois(List<Operation> ops, String moisAnnee) {
        if (moisAnnee == null || moisAnnee.isBlank()) {
            return List.of();
        }
        return ops.stream()
                .filter(o -> o.getTypeOperation() == TypeOperation.COTISATION_MOIS)
                .filter(RapportDonneesHelper::operationComptable)
                .filter(o -> moisAnnee.equals(o.getMoisAnnee())
                        || (o.getMoisAnnee() == null
                                && o.getDateOperation() != null
                                && moisAnnee.equals(o.getDateOperation().format(MOIS_ANNEE))))
                .toList();
    }

    public static BigDecimal montantMoisDu(SuiviMensuel suivi, BigDecimal montantRegleMois) {
        if (suivi != null && suivi.getMontantDu() != null) {
            return suivi.getMontantDu();
        }
        return montantRegleMois != null ? montantRegleMois : BigDecimal.ZERO;
    }

    /** Montant mensuel payé (suivi + opérations COTISATION_MOIS du mois, un seul versement attendu). */
    public static BigDecimal montantMoisPaye(SuiviMensuel suivi, List<Operation> ops, String moisAnnee) {
        BigDecimal payeSuivi =
                suivi != null && suivi.getMontantPaye() != null ? suivi.getMontantPaye() : BigDecimal.ZERO;
        BigDecimal payeOps = cotisationsMoisDuMois(ops, moisAnnee).stream()
                .map(Operation::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return payeSuivi.max(payeOps);
    }

    public static Long resoudreMembreId(Operation op, Map<Long, Compte> comptesParId) {
        if (op.getMembreId() != null) {
            return op.getMembreId();
        }
        if (op.getMouvements() == null || comptesParId == null) {
            return null;
        }
        for (MouvementCompte mc : op.getMouvements()) {
            Compte c = comptesParId.get(mc.getCompteId());
            if (c != null
                    && c.getProprietaire() == ProprietaireCompte.MEMBRE
                    && c.getMembreId() != null) {
                return c.getMembreId();
            }
        }
        return null;
    }

    public static double ratioHebdomadaire(long nbSemainesPayees, int semainesRef) {
        int ref = Math.max(semainesRef, 1);
        return Math.min(1.0, (double) nbSemainesPayees / ref);
    }

    /** Progression mensuelle : 1 versement par mois (indépendant des PLANAD). */
    public static double ratioMensuel(
            SuiviMensuel suivi, List<Operation> ops, String moisAnnee, BigDecimal montantRegleMois) {
        if (!cotisationMensuelleDue(suivi, montantRegleMois)) {
            return 1.0;
        }
        BigDecimal du = montantMoisDu(suivi, montantRegleMois);
        if (du.compareTo(BigDecimal.ZERO) <= 0) {
            return 1.0;
        }
        if (membreMoisAJour(suivi, ops, moisAnnee, montantRegleMois)) {
            return 1.0;
        }
        BigDecimal paye = montantMoisPaye(suivi, ops, moisAnnee);
        if (paye.compareTo(BigDecimal.ZERO) > 0) {
            return Math.min(1.0, paye.divide(du, 4, RoundingMode.HALF_UP).doubleValue());
        }
        return 0.0;
    }

    public static boolean membreMoisAJour(
            SuiviMensuel suivi, List<Operation> ops, String moisAnnee, BigDecimal montantRegleMois) {
        if (!cotisationMensuelleDue(suivi, montantRegleMois)) {
            return true;
        }
        if (suivi != null && suivi.getStatut() == StatutSuiviMensuel.PAYE) {
            return true;
        }
        BigDecimal du = montantMoisDu(suivi, montantRegleMois);
        return montantMoisPaye(suivi, ops, moisAnnee).compareTo(du) >= 0;
    }

    public static boolean membreMoisContribue(
            SuiviMensuel suivi, List<Operation> ops, String moisAnnee, BigDecimal montantRegleMois) {
        if (membreMoisAJour(suivi, ops, moisAnnee, montantRegleMois)) {
            return true;
        }
        return montantMoisPaye(suivi, ops, moisAnnee).compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean membreHebdoAJour(long nbSemainesPayees, int semainesRef) {
        return nbSemainesPayees >= Math.max(semainesRef, 1);
    }

    public static boolean membreAJour(
            long nbSemainesPayees,
            int semainesRef,
            SuiviMensuel suivi,
            List<Operation> ops,
            String moisAnnee,
            BigDecimal montantRegleMois) {
        return membreHebdoAJour(nbSemainesPayees, semainesRef)
                && membreMoisAJour(suivi, ops, moisAnnee, montantRegleMois);
    }

    public static int pctParticipationMoyenne(
            long nbSemainesPayees,
            int semainesRef,
            SuiviMensuel suivi,
            List<Operation> ops,
            String moisAnnee,
            BigDecimal montantRegleMois) {
        double score = (ratioHebdomadaire(nbSemainesPayees, semainesRef)
                        + ratioMensuel(suivi, ops, moisAnnee, montantRegleMois))
                / 2.0;
        return (int) Math.round(score * 100.0);
    }

    public static String cleSemaineCotisation(Operation op) {
        if (op.getObservation() != null) {
            Matcher m = SEMAINE_ISO_OBS.matcher(op.getObservation());
            if (m.find()) {
                return m.group(1).toUpperCase(Locale.ROOT);
            }
        }
        if (op.getDateOperation() != null) {
            WeekFields wf = WeekFields.ISO;
            int y = op.getDateOperation().get(wf.weekBasedYear());
            int w = op.getDateOperation().get(wf.weekOfWeekBasedYear());
            return String.format(Locale.ROOT, "%d-W%02d", y, w);
        }
        return null;
    }

    public static String libelleSemaineGraphique(String cle) {
        Matcher m = SEMAINE_ISO_KEY.matcher(cle);
        if (m.matches()) {
            return "W" + Integer.parseInt(m.group(2));
        }
        try {
            return "W" + SemaineIsoUtil.parserSemaineKey(cle).numeroSemaine();
        } catch (Exception e) {
            return cle;
        }
    }
}
