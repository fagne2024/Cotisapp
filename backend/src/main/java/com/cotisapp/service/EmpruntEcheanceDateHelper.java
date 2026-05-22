package com.cotisapp.service;

import java.time.LocalDate;

public final class EmpruntEcheanceDateHelper {

    private EmpruntEcheanceDateHelper() {}

    /** Date de la nième échéance (1 = premier mois après octroi). */
    public static LocalDate calculerDateEcheance(LocalDate dateOctroi, int numeroEcheance, Integer jourMois) {
        LocalDate d = dateOctroi.plusMonths(numeroEcheance);
        return appliquerJourMois(d, jourMois);
    }

    public static LocalDate calculerDateDerniereEcheance(LocalDate dateOctroi, int nbEcheances, Integer jourMois) {
        if (nbEcheances < 1) {
            return dateOctroi;
        }
        return calculerDateEcheance(dateOctroi, nbEcheances, jourMois);
    }

    private static LocalDate appliquerJourMois(LocalDate date, Integer jourMois) {
        if (jourMois == null || jourMois < 1 || jourMois > 31) {
            return date;
        }
        int day = Math.min(jourMois, date.lengthOfMonth());
        return date.withDayOfMonth(day);
    }
}
