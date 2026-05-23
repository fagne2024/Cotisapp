package com.cotisapp.domain.catalogue;

import com.cotisapp.domain.enums.NiveauDroit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modules du menu GIE : visibles seulement si la section catalogue est active
 * (au moins une action de la section ≠ NO, comme la case « Afficher ce module »)
 * et si une action liée au module est autorisée.
 */
public final class ModuleMenuCatalogue {

    private ModuleMenuCatalogue() {}

    public static final String MEMBRES = "membres";
    public static final String COMPTES = "comptes";
    public static final String COTISATIONS = "cotisations";
    public static final String EMPRUNTS = "emprunts";
    public static final String REMBOURSEMENTS = "remboursements";
    public static final String RAPPORTS = "rapports";
    public static final String EXERCICES = "exercices";
    public static final String TRESORERIE = "tresorerie";
    public static final String PARAMETRAGE = "parametrage";
    public static final String UTILISATEURS = "utilisateurs";
    public static final String NOTIFICATIONS = "notifications";

    private static final String SEC_MEMBRES = "👥 MEMBRES & UTILISATEURS";
    private static final String SEC_OPERATIONS = "💰 OPÉRATIONS FINANCIÈRES";
    private static final String SEC_COMPTES = "🏦 COMPTES & SOLDES";
    private static final String SEC_RAPPORTS = "📈 RAPPORTS & EXPORTS";
    private static final String SEC_PARAM = "⚙ PARAMÉTRAGE & ADMINISTRATION";

    private record ModuleDef(String id, String section, List<String> actionCodes) {}

    private static final List<ModuleDef> MODULES = List.of(
            new ModuleDef(MEMBRES, SEC_MEMBRES, List.of("MEMBRE_LISTER", "MEMBRE_GERER", "MEMBRE_SUSPENDRE", "MEMBRE_CHANGER_POSTE")),
            new ModuleDef(COMPTES, SEC_COMPTES, List.of("SOLDE_ORG", "SOLDE_AUTRES_MEMBRES")),
            new ModuleDef(COTISATIONS, SEC_OPERATIONS, List.of("OP_COTISATION")),
            new ModuleDef(EMPRUNTS, SEC_OPERATIONS, List.of("OP_EMPRUNT")),
            new ModuleDef(REMBOURSEMENTS, SEC_OPERATIONS, List.of("OP_REMBOURSEMENT")),
            new ModuleDef(RAPPORTS, SEC_RAPPORTS, List.of("RAPPORT_COMPLET", "RAPPORT_EXPORT")),
            new ModuleDef(EXERCICES, SEC_PARAM, List.of("PARAM_REGLES")),
            new ModuleDef(TRESORERIE, SEC_OPERATIONS, List.of("OP_DEPENSE", "OP_BANQUE", "OP_PENALITE")),
            new ModuleDef(PARAMETRAGE, SEC_PARAM, List.of("PARAM_REGLES", "ADMIN_SECURITE")),
            new ModuleDef(UTILISATEURS, SEC_PARAM, List.of("ADMIN_UTILISATEURS")),
            new ModuleDef(NOTIFICATIONS, SEC_PARAM, List.of("ADMIN_JOURNAL")));

    public static Map<String, Boolean> calculerModules(Map<String, NiveauDroit> actions) {
        Map<String, String> actionSections = construireActionSections();
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (ModuleDef def : MODULES) {
            boolean sectionActive = sectionCatalogueActive(def.section(), actions, actionSections);
            boolean actionOk = def.actionCodes().stream().anyMatch(code -> estAutorise(actions, code));
            out.put(def.id(), sectionActive && actionOk);
        }
        return out;
    }

    private static Map<String, String> construireActionSections() {
        Map<String, String> map = new LinkedHashMap<>();
        String courante = null;
        for (ActionDroitCatalogue.ActionDef def : ActionDroitCatalogue.toutes()) {
            if (def.section() != null) {
                courante = def.section();
            }
            map.put(def.code(), courante);
        }
        return map;
    }

    private static boolean sectionCatalogueActive(
            String section, Map<String, NiveauDroit> actions, Map<String, String> actionSections) {
        if (section == null) {
            return false;
        }
        for (Map.Entry<String, String> e : actionSections.entrySet()) {
            if (section.equals(e.getValue()) && estAutorise(actions, e.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static boolean estAutorise(Map<String, NiveauDroit> actions, String code) {
        return actions.getOrDefault(code, NiveauDroit.NO) != NiveauDroit.NO;
    }
}
