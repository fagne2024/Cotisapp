package com.cotisapp.domain.catalogue;

import java.util.List;

public final class ActionDroitCatalogue {

    private ActionDroitCatalogue() {}

    public record ActionDef(String code, String section, String libelle, int ordre) {}

    public static List<ActionDef> toutes() {
        return List.of(
                action("ORG_LISTER", "🌐 ORGANISATIONS", "Voir toutes les organisations", 10),
                action("ORG_GERER", null, "Créer / modifier une organisation", 11),
                action("ORG_SUSPENDRE", null, "Suspendre / réactiver une organisation", 12),
                action("MEMBRE_LISTER", "👥 MEMBRES & UTILISATEURS", "Voir tous les membres", 20),
                action("MEMBRE_GERER", null, "Ajouter / modifier un membre", 21),
                action("MEMBRE_SUSPENDRE", null, "Suspendre / exclure un membre", 22),
                action("MEMBRE_CHANGER_POSTE", null, "Changer le poste bureau d'un membre", 23),
                action("PROFIL_VOIR_SIEN", null, "Voir son propre profil", 24),
                action("OP_COTISATION", "💰 OPÉRATIONS FINANCIÈRES", "Saisir cotisation / versement / mois", 30),
                action("OP_EMPRUNT", null, "Accorder un emprunt", 31),
                action("OP_REMBOURSEMENT", null, "Enregistrer un remboursement", 32),
                action("OP_PENALITE", null, "Appliquer une pénalité / amende", 33),
                action("OP_DEPENSE", null, "Enregistrer une dépense", 34),
                action("OP_BANQUE", null, "Opérations bancaires (versement/retrait)", 35),
                action("OP_ANNULER", null, "Annuler / contrepasser une opération", 36),
                action("SOLDE_ORG", "🏦 COMPTES & SOLDES", "Voir les soldes de l'organisation (Caisse, Banque, Sol.)", 40),
                action("SOLDE_SIEN", null, "Voir ses propres soldes (comptes membre)", 41),
                action("SOLDE_AUTRES_MEMBRES", null, "Voir les soldes des autres membres", 42),
                action("RAPPORT_COMPLET", "📈 RAPPORTS & EXPORTS", "Accéder aux rapports complets", 50),
                action("RAPPORT_EXPORT", null, "Exporter PDF / Excel", 51),
                action("RAPPORT_HISTORIQUE_SIEN", null, "Voir son propre historique", 52),
                action("PARAM_REGLES", "⚙ PARAMÉTRAGE & ADMINISTRATION", "Configurer les règles comptables", 60),
                action("ADMIN_UTILISATEURS", null, "Gérer les utilisateurs et droits", 61),
                action("ADMIN_JOURNAL", null, "Voir le journal des connexions", 62),
                action("ADMIN_RESET_MDP", null, "Réinitialiser les mots de passe", 63),
                action("ADMIN_SECURITE", null, "Configurer la sécurité (2FA, sessions)", 64));
    }

    private static ActionDef action(String code, String section, String libelle, int ordre) {
        return new ActionDef(code, section, libelle, ordre);
    }
}
