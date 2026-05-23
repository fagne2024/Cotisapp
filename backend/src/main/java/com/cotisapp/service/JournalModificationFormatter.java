package com.cotisapp.service;

import com.cotisapp.domain.enums.NiveauDroit;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Construit des détails de journal listant explicitement ce qui a été modifié. */
public final class JournalModificationFormatter {

    private JournalModificationFormatter() {}

    public static String champModifie(String libelle, Object avant, Object apres) {
        String a = normaliser(avant);
        String b = normaliser(apres);
        if (Objects.equals(a, b)) {
            return null;
        }
        return libelle + " : " + afficher(avant) + " → " + afficher(apres);
    }

    public static void ajouterSiChange(List<String> changements, String libelle, Object avant, Object apres) {
        String ligne = champModifie(libelle, avant, apres);
        if (ligne != null) {
            changements.add(ligne);
        }
    }

    public static String resumeModifications(String cible, List<String> changements) {
        if (changements == null || changements.isEmpty()) {
            return cible + " — aucun champ modifié (enregistrement sans changement détecté)";
        }
        return cible + " — Modifications : " + String.join(" ; ", changements);
    }

    public static String resumeCreation(String cible, String... attributs) {
        List<String> parts = new ArrayList<>();
        for (String a : attributs) {
            if (a != null && !a.isBlank()) {
                parts.add(a);
            }
        }
        if (parts.isEmpty()) {
            return "Création — " + cible;
        }
        return "Création — " + cible + " (" + String.join(" ; ", parts) + ")";
    }

    public static String resumeAction(String action, String cible, String complement) {
        StringBuilder sb = new StringBuilder(action);
        if (cible != null && !cible.isBlank()) {
            sb.append(" — ").append(cible);
        }
        if (complement != null && !complement.isBlank()) {
            sb.append(" — ").append(complement);
        }
        return sb.toString();
    }

    public static String cibleUtilisateur(String prenom, String nom, String email, Long id) {
        String nomComplet = ((prenom != null ? prenom.trim() : "")
                        + " "
                        + (nom != null ? nom.trim() : ""))
                .trim();
        if (!nomComplet.isBlank() && email != null && !email.isBlank()) {
            return nomComplet + " (« " + email + " »)";
        }
        if (!nomComplet.isBlank()) {
            return nomComplet;
        }
        if (email != null && !email.isBlank()) {
            return "« " + email + " »";
        }
        return id != null ? "utilisateur n°" + id : "utilisateur";
    }

    public static String cibleMembre(String codeMembre, String prenom, String nom, Long id) {
        String nomComplet = ((prenom != null ? prenom.trim() : "")
                        + " "
                        + (nom != null ? nom.trim() : ""))
                .trim();
        if (codeMembre != null && !codeMembre.isBlank() && !nomComplet.isBlank()) {
            return codeMembre + " — " + nomComplet;
        }
        if (codeMembre != null && !codeMembre.isBlank()) {
            return "membre " + codeMembre;
        }
        if (!nomComplet.isBlank()) {
            return nomComplet;
        }
        return id != null ? "membre n°" + id : "membre";
    }

    public static String libelleRole(Role role) {
        if (role == null) {
            return "—";
        }
        return switch (role) {
            case SUPERADMIN -> "Superadmin";
            case ADMIN_GIE -> "Admin GIE";
            case MEMBRE -> "Membre";
        };
    }

    public static String libellePoste(PosteMembre poste) {
        if (poste == null) {
            return "—";
        }
        return switch (poste) {
            case SIMPLE -> "Membre simple";
            case PRESIDENT -> "Président(e)";
            case VICE_PRESIDENT -> "Vice-président(e)";
            case SECRETAIRE_GENERAL -> "Secrétaire général";
            case SECRETAIRE_GENERAL_ADJOINT -> "Secrétaire général adjoint";
            case TRESORIER -> "Trésorier(ère)";
            case SUPERVISEUR -> "Superviseur";
        };
    }

    public static String libelleActif(Boolean actif) {
        return Boolean.TRUE.equals(actif) ? "actif" : "suspendu";
    }

    public static String libelleNiveauDroit(NiveauDroit niveau) {
        if (niveau == null) {
            return "aucun";
        }
        return switch (niveau) {
            case NO -> "aucun";
            case OK -> "complet";
            case LIM -> "son GIE";
            case OWN -> "le sien";
        };
    }

    public static String montantFcfa(BigDecimal montant) {
        if (montant == null) {
            return "—";
        }
        return String.format(Locale.FRENCH, "%,.0f F", montant);
    }

    public static String resumeDroitsModifies(String profilLibelle, List<String> changements, int total) {
        if (changements.isEmpty()) {
            return "Droits du profil « " + profilLibelle + " » — aucune modification";
        }
        int affiches = changements.size();
        String liste = String.join(" ; ", changements);
        if (total > affiches) {
            liste += " ; … et " + (total - affiches) + " autre(s)";
        }
        return "Droits du profil « " + profilLibelle + " » — "
                + total
                + " modification(s) : "
                + liste;
    }

    private static String normaliser(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s.trim();
        }
        if (value instanceof Boolean b) {
            return b.toString();
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        return value.toString().trim();
    }

    private static String afficher(Object value) {
        if (value == null) {
            return "(vide)";
        }
        if (value instanceof Boolean b) {
            return libelleActif(b);
        }
        if (value instanceof Role r) {
            return libelleRole(r);
        }
        if (value instanceof PosteMembre p) {
            return libellePoste(p);
        }
        if (value instanceof NiveauDroit n) {
            return libelleNiveauDroit(n);
        }
        if (value instanceof BigDecimal bd) {
            return montantFcfa(bd);
        }
        String s = value.toString().trim();
        return s.isEmpty() ? "(vide)" : s;
    }
}
