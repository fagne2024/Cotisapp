import type { TypeProfilDroitDto } from '../../core/services/type-profil.service';
import type { MenuModuleId } from '../../core/util/modules-menu.util';
import type { NiveauDroitUi } from './utilisateurs-droits.util';

/** Section catalogue affichée dans le panneau droits (hors organisations plateforme). */
export const SECTIONS_DROITS_ORG = [
  '👥 MEMBRES & UTILISATEURS',
  '💰 OPÉRATIONS FINANCIÈRES',
  '🏦 COMPTES & SOLDES',
  '📈 RAPPORTS & EXPORTS',
  '⚙ PARAMÉTRAGE & ADMINISTRATION',
] as const;

const SEC_MEMBRES = '👥 MEMBRES & UTILISATEURS';
const SEC_OPERATIONS = '💰 OPÉRATIONS FINANCIÈRES';
const SEC_COMPTES = '🏦 COMPTES & SOLDES';
const SEC_RAPPORTS = '📈 RAPPORTS & EXPORTS';
const SEC_PARAM = '⚙ PARAMÉTRAGE & ADMINISTRATION';

const MODULES: { id: MenuModuleId; section: string; actionCodes: string[] }[] = [
  { id: 'membres', section: SEC_MEMBRES, actionCodes: ['MEMBRE_LISTER', 'MEMBRE_GERER', 'MEMBRE_SUSPENDRE', 'MEMBRE_CHANGER_POSTE'] },
  { id: 'comptes', section: SEC_COMPTES, actionCodes: ['SOLDE_ORG', 'SOLDE_AUTRES_MEMBRES'] },
  { id: 'cotisations', section: SEC_OPERATIONS, actionCodes: ['OP_COTISATION'] },
  { id: 'emprunts', section: SEC_OPERATIONS, actionCodes: ['OP_EMPRUNT'] },
  { id: 'remboursements', section: SEC_OPERATIONS, actionCodes: ['OP_REMBOURSEMENT'] },
  { id: 'rapports', section: SEC_RAPPORTS, actionCodes: ['RAPPORT_COMPLET', 'RAPPORT_EXPORT'] },
  { id: 'exercices', section: SEC_PARAM, actionCodes: ['PARAM_REGLES'] },
  { id: 'tresorerie', section: SEC_OPERATIONS, actionCodes: ['OP_DEPENSE', 'OP_BANQUE', 'OP_PENALITE'] },
  { id: 'parametrage', section: SEC_PARAM, actionCodes: ['PARAM_REGLES', 'ADMIN_SECURITE'] },
  { id: 'utilisateurs', section: SEC_PARAM, actionCodes: ['ADMIN_UTILISATEURS'] },
  { id: 'notifications', section: SEC_PARAM, actionCodes: ['ADMIN_JOURNAL'] },
];

const LIBELLES_MODULES: Record<MenuModuleId, string> = {
  membres: 'Membres',
  comptes: 'Comptes & Relevés',
  cotisations: 'Cotisation',
  emprunts: 'Emprunts',
  remboursements: 'Remboursement',
  rapports: 'Rapports',
  exercices: 'Exercices',
  tresorerie: 'Trésorerie',
  parametrage: 'Paramétrage',
  utilisateurs: 'Utilisateurs & Droits',
  notifications: 'Notifications',
};

function construireMapsDepuisLignes(lignes: TypeProfilDroitDto[]): {
  actions: Record<string, string>;
  actionSections: Record<string, string | null>;
} {
  const actions: Record<string, string> = {};
  const actionSections: Record<string, string | null> = {};
  let courante: string | null = null;
  for (const l of lignes) {
    if (l.section) {
      courante = l.section;
    }
    actionSections[l.actionCode] = courante ?? l.section ?? null;
    actions[l.actionCode] = l.niveau;
  }
  return { actions, actionSections };
}

function sectionCatalogueActive(
  section: string,
  actions: Record<string, string>,
  actionSections: Record<string, string | null>
): boolean {
  for (const [code, sec] of Object.entries(actionSections)) {
    if (sec === section && actions[code] != null && actions[code] !== 'NO') {
      return true;
    }
  }
  return false;
}

/** Calcule les modules menu visibles (même règle que le backend /mes-droits). */
export function calculerModulesDepuisLignes(lignes: TypeProfilDroitDto[]): Record<string, boolean> {
  const { actions, actionSections } = construireMapsDepuisLignes(lignes);
  const out: Record<string, boolean> = {};
  for (const def of MODULES) {
    const sectionActive = sectionCatalogueActive(def.section, actions, actionSections);
    const actionOk = def.actionCodes.some((code) => actions[code] != null && actions[code] !== 'NO');
    out[def.id] = sectionActive && actionOk;
  }
  return out;
}

export function libellesModulesMenuActifs(modules: Record<string, boolean>): string[] {
  return MODULES.filter((m) => modules[m.id]).map((m) => LIBELLES_MODULES[m.id]);
}

export function moduleSectionActif(lignes: TypeProfilDroitDto[], sectionLabel: string): boolean {
  return sectionCatalogueActive(
    sectionLabel,
    construireMapsDepuisLignes(lignes).actions,
    construireMapsDepuisLignes(lignes).actionSections
  );
}

export function estSectionOrgConfigurable(section: string | null | undefined): boolean {
  return !!section && (SECTIONS_DROITS_ORG as readonly string[]).includes(section);
}

/** Active/désactive toutes les actions d'une section (case « module menu »). */
export function appliquerModuleSection(
  lignes: TypeProfilDroitDto[],
  sectionLabel: string,
  actif: boolean
): TypeProfilDroitDto[] {
  let cur: string | null = null;
  return lignes.map((l) => {
    if (l.section) {
      cur = l.section;
    }
    if (cur !== sectionLabel) {
      return l;
    }
    if (!actif) {
      return { ...l, niveau: 'NO' as NiveauDroitUi };
    }
    if (l.niveau === 'NO') {
      return { ...l, niveau: 'LIM' as NiveauDroitUi };
    }
    return l;
  });
}
