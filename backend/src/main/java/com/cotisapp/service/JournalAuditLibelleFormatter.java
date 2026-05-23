package com.cotisapp.service;

import com.cotisapp.domain.entity.JournalAudit;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.domain.enums.TypeEvenementJournal;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Libellés lisibles pour le journal des actions utilisateurs. */
public final class JournalAuditLibelleFormatter {

    private static final Pattern OP_ID = Pattern.compile("(?:opération|operation|emprunt)\\s*#?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("COTISATION", "Cotisation hebdomadaire enregistrée"),
            Map.entry("COTISATION_MOIS", "Cotisation mensuelle enregistrée"),
            Map.entry("DEPENSE", "Dépense / sortie caisse enregistrée"),
            Map.entry("BANQUE_VERSEMENT", "Mouvement banque enregistré"),
            Map.entry("BANQUE_RETRAIT", "Retrait banque enregistré"),
            Map.entry("EMPRUNT", "Octroi d'emprunt enregistré"),
            Map.entry("REMBOURSEMENT", "Remboursement enregistré"),
            Map.entry("REMBOURSEMENT_CAISSE", "Remboursement via caisse enregistré"),
            Map.entry("REMBOURSEMENT_SOLIDARITE", "Remboursement solidarité enregistré"),
            Map.entry("PENALITE", "Pénalité appliquée"),
            Map.entry("AMENDE", "Amende appliquée"),
            Map.entry("ANNULATION_COTISATION", "Annulation de cotisation"),
            Map.entry("ANNULATION_REMBOURSEMENT", "Annulation de remboursement"),
            Map.entry("ANNULATION_EMPRUNT", "Annulation d'emprunt"),
            Map.entry("PROFIL_MAJ", "Mise à jour du profil utilisateur"),
            Map.entry("MOT_DE_PASSE_MAJ", "Changement de mot de passe"),
            Map.entry("2FA_SETUP_DEMARRE", "Configuration 2FA démarrée"),
            Map.entry("2FA_ACTIVE", "Double authentification activée"),
            Map.entry("2FA_DESACTIVE", "Double authentification désactivée"),
            Map.entry("CONNEXION_REUSSIE", "Connexion réussie"),
            Map.entry("CONNEXION_ECHEC", "Tentative de connexion refusée"),
            Map.entry("DECONNEXION", "Déconnexion"),
            Map.entry("MODULE_VISITE", "Consultation d'un écran"),
            Map.entry("MEMBRE_CREATION", "Création d'un membre"),
            Map.entry("MEMBRE_MAJ", "Fiche membre modifiée"),
            Map.entry("UTILISATEUR_CREATION", "Création d'un compte utilisateur"),
            Map.entry("UTILISATEUR_MAJ", "Compte utilisateur modifié"),
            Map.entry("TYPE_PROFIL_CREATION", "Type de profil créé"),
            Map.entry("TYPE_PROFIL_MAJ", "Type de profil modifié"),
            Map.entry("TYPE_PROFIL_SUPPRESSION", "Type de profil supprimé"),
            Map.entry("DROITS_PROFIL_MAJ", "Droits de profil modifiés"),
            Map.entry("EXERCICE_TRANSITION", "Clôture / ouverture d'exercice"),
            Map.entry("EXERCICE_REOUVERTURE", "Réouverture d'exercice"));

    private JournalAuditLibelleFormatter() {}

    public static String titre(JournalAudit j) {
        if (j == null) {
            return "—";
        }
        return switch (j.getTypeEvenement() != null ? j.getTypeEvenement() : TypeEvenementJournal.ACTION_METIER) {
            case CONNEXION -> "Connexion réussie";
            case DECONNEXION -> "Déconnexion";
            case CONNEXION_ECHEC -> "Échec de connexion";
            case MODULE_VISITE, NAVIGATION -> titreVisite(j);
            case SECURITE -> libelleAction(j.getAction()) + (j.getSucces() != null && !j.getSucces() ? " (refusé)" : "");
            case ACTION_METIER -> libelleAction(j.getAction());
        };
    }

    public static String detailAffichage(JournalAudit j) {
        if (j == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        String utilisateur = formatUtilisateur(j);
        if (!utilisateur.isBlank()) {
            sb.append(utilisateur);
        }

        switch (j.getTypeEvenement() != null ? j.getTypeEvenement() : TypeEvenementJournal.ACTION_METIER) {
            case CONNEXION -> appendConnexion(sb, j, true);
            case DECONNEXION -> {
                if (!sb.isEmpty()) {
                    sb.append(" s'est déconnecté de l'application.");
                } else {
                    sb.append("Déconnexion de l'application.");
                }
            }
            case CONNEXION_ECHEC -> appendConnexionEchec(sb, j);
            case MODULE_VISITE, NAVIGATION -> appendVisite(sb, j);
            case ACTION_METIER, SECURITE -> appendActionMetier(sb, j);
        }

        String texte = sb.toString().trim();
        if (texte.isEmpty() && j.getDetails() != null) {
            texte = enrichirDetailsAction(j.getAction(), j.getDetails());
        }
        return texte.isEmpty() ? null : texte;
    }

    public static String libelleResume(JournalAudit j) {
        String titre = titre(j);
        if (j.getModuleLibelle() != null
                && !j.getModuleLibelle().isBlank()
                && (j.getTypeEvenement() == TypeEvenementJournal.MODULE_VISITE
                        || j.getTypeEvenement() == TypeEvenementJournal.NAVIGATION)) {
            return titre + " — " + j.getModuleLibelle();
        }
        return titre;
    }

    public static String libelleAction(String action) {
        if (action == null || action.isBlank()) {
            return "Action";
        }
        return ACTIONS.getOrDefault(action, humaniserCode(action));
    }

    public static String enrichirDetailsAction(String action, String details) {
        String libelle = libelleAction(action);
        if (details == null || details.isBlank()) {
            return libelle;
        }
        Matcher m = OP_ID.matcher(details);
        if (m.find()) {
            String ref = m.group(1);
            String typeRef = details.toLowerCase(Locale.ROOT).contains("emprunt") ? "emprunt" : "opération";
            return libelle + " (réf. " + typeRef + " n°" + ref + ")";
        }
        if (details.equals(libelle) || details.startsWith(libelle)) {
            return details;
        }
        return libelle + " — " + details;
    }

    public static String resumeNavigateur(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String ua = userAgent;
        String navigateur = "Navigateur";
        if (ua.contains("Edg/")) {
            navigateur = "Microsoft Edge";
        } else if (ua.contains("Chrome/") && !ua.contains("Edg/")) {
            navigateur = "Google Chrome";
        } else if (ua.contains("Firefox/")) {
            navigateur = "Mozilla Firefox";
        } else if (ua.contains("Safari/") && !ua.contains("Chrome/")) {
            navigateur = "Safari";
        }

        String os = null;
        if (ua.contains("Windows")) {
            os = "Windows";
        } else if (ua.contains("Mac OS")) {
            os = "macOS";
        } else if (ua.contains("Android")) {
            os = "Android";
        } else if (ua.contains("iPhone") || ua.contains("iPad")) {
            os = "iOS";
        } else if (ua.contains("Linux")) {
            os = "Linux";
        }

        return os != null ? navigateur + " · " + os : navigateur;
    }

    public static String libelleTypeCourt(TypeEvenementJournal type) {
        if (type == null) {
            return "Action";
        }
        return switch (type) {
            case CONNEXION -> "Connexion";
            case DECONNEXION -> "Déconnexion";
            case CONNEXION_ECHEC -> "Échec connexion";
            case MODULE_VISITE -> "Consultation écran";
            case ACTION_METIER -> "Action métier";
            case NAVIGATION -> "Navigation";
            case SECURITE -> "Sécurité";
        };
    }

    public static String libelleRole(Role role) {
        if (role == null) {
            return "Utilisateur";
        }
        return switch (role) {
            case SUPERADMIN -> "Superadmin";
            case ADMIN_GIE -> "Admin GIE";
            case MEMBRE -> "Membre";
        };
    }

    private static String titreVisite(JournalAudit j) {
        if (j.getModuleLibelle() != null && !j.getModuleLibelle().isBlank()) {
            return "Ouverture de l'écran « " + j.getModuleLibelle() + " »";
        }
        return "Navigation dans l'application";
    }

    private static void appendVisite(StringBuilder sb, JournalAudit j) {
        if (!sb.isEmpty()) {
            sb.append(" a consulté");
        } else {
            sb.append("Consultation de");
        }
        if (j.getModuleLibelle() != null && !j.getModuleLibelle().isBlank()) {
            sb.append(" l'écran « ").append(j.getModuleLibelle()).append(" »");
        } else {
            sb.append(" un module");
        }
        if (j.getRoutePath() != null && !j.getRoutePath().isBlank()) {
            sb.append(" — chemin ").append(j.getRoutePath());
        }
        sb.append(".");
    }

    private static void appendConnexion(StringBuilder sb, JournalAudit j, boolean reussie) {
        if (!sb.isEmpty()) {
            sb.append(reussie ? " s'est connecté" : " — connexion");
        } else {
            sb.append(reussie ? "Connexion réussie" : "Connexion");
        }
        sb.append(" en tant que ").append(libelleRole(j.getRole()));
        if (j.getDetails() != null && !j.getDetails().isBlank()) {
            sb.append(" · ").append(j.getDetails());
        }
        sb.append(".");
    }

    private static void appendConnexionEchec(StringBuilder sb, JournalAudit j) {
        if (j.getUtilisateurEmail() != null && !j.getUtilisateurEmail().isBlank()) {
            sb.append("Identifiant « ").append(j.getUtilisateurEmail()).append(" » : ");
        }
        sb.append(j.getDetails() != null ? j.getDetails() : "authentification refusée");
        sb.append(".");
    }

    private static void appendActionMetier(StringBuilder sb, JournalAudit j) {
        if (!sb.isEmpty()) {
            sb.append(" — ");
        }
        String details = j.getDetails();
        if (details != null && details.contains("Modifications :")) {
            sb.append(details);
        } else if (details != null && (details.startsWith("Création —") || details.startsWith("Droits du profil"))) {
            sb.append(details);
        } else {
            sb.append(enrichirDetailsAction(j.getAction(), details));
        }
        if (j.getRoutePath() != null && !j.getRoutePath().isBlank()) {
            sb.append(" (écran ").append(j.getRoutePath()).append(")");
        }
        sb.append(".");
    }

    private static String formatUtilisateur(JournalAudit j) {
        if (j.getUtilisateurNom() != null && !j.getUtilisateurNom().isBlank()) {
            return j.getUtilisateurNom().trim();
        }
        if (j.getUtilisateurEmail() != null && !j.getUtilisateurEmail().isBlank()) {
            return j.getUtilisateurEmail().trim();
        }
        return "";
    }

    private static String humaniserCode(String code) {
        return code.replace('_', ' ').toLowerCase(Locale.FRENCH);
    }
}
