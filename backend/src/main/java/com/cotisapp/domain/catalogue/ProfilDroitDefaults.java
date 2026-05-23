package com.cotisapp.domain.catalogue;

import com.cotisapp.domain.enums.NiveauDroit;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Matrices de droits par défaut pour les profils applicatifs (création org + réinitialisation).
 */
public final class ProfilDroitDefaults {

    private ProfilDroitDefaults() {}

    public static Map<String, NiveauDroit> pourProfil(Role role, PosteMembre poste) {
        if (role == Role.ADMIN_GIE) {
            return adminGie();
        }
        if (poste == null || poste == PosteMembre.SIMPLE) {
            return membreSimple();
        }
        return switch (poste) {
            case SECRETAIRE_GENERAL -> secretaireGeneral();
            case SECRETAIRE_GENERAL_ADJOINT -> secretaireGeneralAdjoint();
            case TRESORIER -> tresorier();
            case TRESORIER_ADJOINT -> tresorierAdjoint();
            case COMMISSAIRE_AUX_COMPTES -> commissaireAuxComptes();
            case SUPERVISEUR -> superviseur();
            case PRESIDENT -> president();
            default -> membreSimple();
        };
    }

    /** Tous les droits organisation (sauf gestion multi-org réservée au superadmin). */
    public static Map<String, NiveauDroit> adminGie() {
        Map<String, NiveauDroit> m = baseToutNon();
        for (ActionDroitCatalogue.ActionDef def : ActionDroitCatalogue.toutes()) {
            String code = def.code();
            if (!code.startsWith("ORG_")) {
                m.put(code, NiveauDroit.LIM);
            }
        }
        m.put("PROFIL_VOIR_SIEN", NiveauDroit.OK);
        m.put("SOLDE_SIEN", NiveauDroit.OK);
        m.put("RAPPORT_HISTORIQUE_SIEN", NiveauDroit.OK);
        return m;
    }

    /** Accès personnel uniquement. */
    public static Map<String, NiveauDroit> membreSimple() {
        Map<String, NiveauDroit> m = baseToutNon();
        m.put("PROFIL_VOIR_SIEN", NiveauDroit.OK);
        m.put("SOLDE_SIEN", NiveauDroit.OWN);
        m.put("RAPPORT_HISTORIQUE_SIEN", NiveauDroit.OWN);
        return m;
    }

    /**
     * Secrétaire général : membres, cotisations, emprunts, rapports ; pas trésorerie ni admin utilisateurs.
     */
    public static Map<String, NiveauDroit> secretaireGeneral() {
        Map<String, NiveauDroit> m = baseBureauCommun();
        m.put("MEMBRE_GERER", NiveauDroit.LIM);
        m.put("MEMBRE_CHANGER_POSTE", NiveauDroit.LIM);
        m.put("OP_COTISATION", NiveauDroit.LIM);
        m.put("OP_EMPRUNT", NiveauDroit.LIM);
        m.put("OP_REMBOURSEMENT", NiveauDroit.LIM);
        m.put("OP_PENALITE", NiveauDroit.LIM);
        m.put("OP_ANNULER", NiveauDroit.LIM);
        m.put("PARAM_REGLES", NiveauDroit.LIM);
        m.put("ADMIN_JOURNAL", NiveauDroit.LIM);
        return m;
    }

    /** S.G. adjoint : comme SG sans changement de poste ni annulation d'opérations. */
    public static Map<String, NiveauDroit> secretaireGeneralAdjoint() {
        Map<String, NiveauDroit> m = baseBureauCommun();
        m.put("MEMBRE_GERER", NiveauDroit.LIM);
        m.put("OP_COTISATION", NiveauDroit.LIM);
        m.put("OP_EMPRUNT", NiveauDroit.LIM);
        m.put("OP_REMBOURSEMENT", NiveauDroit.LIM);
        m.put("OP_PENALITE", NiveauDroit.LIM);
        m.put("ADMIN_JOURNAL", NiveauDroit.LIM);
        return m;
    }

    /** Trésorier(ère) : opérations financières et soldes organisation. */
    public static Map<String, NiveauDroit> tresorier() {
        Map<String, NiveauDroit> m = baseBureauCommun();
        m.put("OP_COTISATION", NiveauDroit.LIM);
        m.put("OP_EMPRUNT", NiveauDroit.LIM);
        m.put("OP_REMBOURSEMENT", NiveauDroit.LIM);
        m.put("OP_PENALITE", NiveauDroit.LIM);
        m.put("OP_DEPENSE", NiveauDroit.LIM);
        m.put("OP_BANQUE", NiveauDroit.LIM);
        m.put("OP_ANNULER", NiveauDroit.LIM);
        m.put("RAPPORT_EXPORT", NiveauDroit.LIM);
        m.put("ADMIN_JOURNAL", NiveauDroit.LIM);
        return m;
    }

    /** Trésorier(ère) adjoint : finances sans dépenses ni annulation d'opérations. */
    public static Map<String, NiveauDroit> tresorierAdjoint() {
        Map<String, NiveauDroit> m = baseBureauCommun();
        m.put("OP_COTISATION", NiveauDroit.LIM);
        m.put("OP_EMPRUNT", NiveauDroit.LIM);
        m.put("OP_REMBOURSEMENT", NiveauDroit.LIM);
        m.put("OP_PENALITE", NiveauDroit.LIM);
        m.put("OP_BANQUE", NiveauDroit.LIM);
        m.put("RAPPORT_EXPORT", NiveauDroit.LIM);
        m.put("ADMIN_JOURNAL", NiveauDroit.LIM);
        return m;
    }

    /** Commissaire au compte : contrôle, rapports exportables, pas de saisie. */
    public static Map<String, NiveauDroit> commissaireAuxComptes() {
        Map<String, NiveauDroit> m = baseBureauCommun();
        m.put("RAPPORT_EXPORT", NiveauDroit.LIM);
        m.put("ADMIN_JOURNAL", NiveauDroit.LIM);
        return m;
    }

    /** Superviseur : consultation et rapports, pas de saisie d'opérations. */
    public static Map<String, NiveauDroit> superviseur() {
        Map<String, NiveauDroit> m = baseBureauCommun();
        m.put("ADMIN_JOURNAL", NiveauDroit.LIM);
        return m;
    }

    /** Président(e) : vision d'ensemble et validation des opérations clés. */
    public static Map<String, NiveauDroit> president() {
        Map<String, NiveauDroit> m = baseBureauCommun();
        m.put("MEMBRE_GERER", NiveauDroit.LIM);
        m.put("OP_COTISATION", NiveauDroit.LIM);
        m.put("OP_EMPRUNT", NiveauDroit.LIM);
        m.put("OP_REMBOURSEMENT", NiveauDroit.LIM);
        m.put("OP_PENALITE", NiveauDroit.LIM);
        m.put("ADMIN_JOURNAL", NiveauDroit.LIM);
        return m;
    }

    private static Map<String, NiveauDroit> baseBureauCommun() {
        Map<String, NiveauDroit> m = baseToutNon();
        m.put("PROFIL_VOIR_SIEN", NiveauDroit.OK);
        m.put("SOLDE_SIEN", NiveauDroit.OK);
        m.put("RAPPORT_HISTORIQUE_SIEN", NiveauDroit.OK);
        m.put("MEMBRE_LISTER", NiveauDroit.LIM);
        m.put("SOLDE_ORG", NiveauDroit.LIM);
        m.put("SOLDE_AUTRES_MEMBRES", NiveauDroit.LIM);
        m.put("RAPPORT_COMPLET", NiveauDroit.LIM);
        return m;
    }

    private static Map<String, NiveauDroit> baseToutNon() {
        Map<String, NiveauDroit> m = new HashMap<>();
        for (ActionDroitCatalogue.ActionDef def : ActionDroitCatalogue.toutes()) {
            m.put(def.code(), NiveauDroit.NO);
        }
        return m;
    }

    /** Libellés métier des postes bureau (documentation / API). */
    public static Map<PosteMembre, String> libellesPosteBureau() {
        Map<PosteMembre, String> map = new EnumMap<>(PosteMembre.class);
        map.put(PosteMembre.SECRETAIRE_GENERAL, "Secrétaire général");
        map.put(PosteMembre.SECRETAIRE_GENERAL_ADJOINT, "Secrétaire général adjoint");
        map.put(PosteMembre.TRESORIER, "Trésorier(ère)");
        map.put(PosteMembre.TRESORIER_ADJOINT, "Trésorier(ère) adjoint");
        map.put(PosteMembre.COMMISSAIRE_AUX_COMPTES, "Commissaire au compte");
        map.put(PosteMembre.SUPERVISEUR, "Superviseur");
        map.put(PosteMembre.PRESIDENT, "Président(e)");
        return map;
    }
}
