package com.cotisapp.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SemaineIsoUtil {

    private static final Pattern SEMAINE_KEY = Pattern.compile("^(\\d{4})-W(\\d{1,2})$");
    private static final DateTimeFormatter JOUR_COURT =
            DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH);

    private SemaineIsoUtil() {}

    public record BornesSemaine(LocalDate lundi, LocalDate dimanche, int numeroSemaine, int annee) {}

    public static BornesSemaine parserSemaineKey(String semaineKey) {
        Matcher m = SEMAINE_KEY.matcher(semaineKey != null ? semaineKey.trim() : "");
        if (!m.matches()) {
            throw new IllegalArgumentException("Semaine invalide: " + semaineKey);
        }
        int year = Integer.parseInt(m.group(1));
        int week = Integer.parseInt(m.group(2));
        // ISO : jour 1 = lundi, jour 7 = dimanche de la semaine
        LocalDate lundi = LocalDate.parse(
                String.format(Locale.ROOT, "%d-W%02d-1", year, week),
                DateTimeFormatter.ISO_WEEK_DATE);
        LocalDate dimanche = lundi.plusDays(6);
        return new BornesSemaine(lundi, dimanche, week, year);
    }

    public static String libelleSemaine(String semaineKey) {
        BornesSemaine b = parserSemaineKey(semaineKey);
        return String.format(
                Locale.FRENCH,
                "Semaine %d — du %s au %s",
                b.numeroSemaine(),
                b.lundi().format(JOUR_COURT),
                b.dimanche().format(JOUR_COURT));
    }

    public static String marqueurObservation(String semaineKey) {
        return "[" + semaineKey + "]";
    }
}
