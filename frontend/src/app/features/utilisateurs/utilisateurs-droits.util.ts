import { environment } from '../../../environments/environment';
import type { PosteMembreApi } from '../membres/membres-poste.util';

export type UtilisateursTab = 'users' | 'journal' | 'droits' | 'types';

/** Raccourcis création compte bureau → profil applicatif + poste. */
export const PROFILS_BUREAU_RAPIDES: {
  code: string;
  poste: PosteMembreApi;
  label: string;
  description: string;
}[] = [
  {
    code: 'SG',
    poste: 'SECRETAIRE_GENERAL',
    label: 'Secrétaire général',
    description: 'Membres, cotisations, emprunts, rapports',
  },
  {
    code: 'SGA',
    poste: 'SECRETAIRE_GENERAL_ADJOINT',
    label: 'S.G. adjoint',
    description: 'Cotisations, emprunts (sans annulation ni changement de poste)',
  },
  {
    code: 'TRESORIER',
    poste: 'TRESORIER',
    label: 'Trésorier(ère)',
    description: 'Caisse, banque, dépenses, remboursements',
  },
  {
    code: 'SUPERVISEUR',
    poste: 'SUPERVISEUR',
    label: 'Superviseur',
    description: 'Consultation et rapports (pas de saisie)',
  },
  {
    code: 'PRESIDENT',
    poste: 'PRESIDENT',
    label: 'Président(e)',
    description: 'Vision d’ensemble et validation des opérations clés',
  },
];

/** Rôle affiché dans le formulaire création utilisateur (mappé vers ADMIN_GIE ou MEMBRE à l'enregistrement). */
export type RoleFormUi = 'ADMIN_GIE' | 'MEMBRE_BUREAU' | 'MEMBRE_SIMPLE';

export function roleApiDepuisFormUi(ui: RoleFormUi): 'ADMIN_GIE' | 'MEMBRE' {
  return ui === 'ADMIN_GIE' ? 'ADMIN_GIE' : 'MEMBRE';
}

export function estPosteBureau(poste: string | null | undefined): boolean {
  return !!poste && poste !== 'SIMPLE';
}

const DROITS_DEBUG = !environment.production;

/** Logs console pour le diagnostic droits par profil (désactivés en production). */
export function logDroits(message: string, data?: unknown): void {
  if (!DROITS_DEBUG) {
    return;
  }
  if (data !== undefined) {
    console.info(`[Droits] ${message}`, data);
  } else {
    console.info(`[Droits] ${message}`);
  }
}

export function resumeNiveauxDroits(
  lignes: { actionCode: string; niveau: string }[]
): Record<string, number> {
  const counts: Record<string, number> = { OK: 0, NO: 0, LIM: 0, OWN: 0 };
  for (const l of lignes) {
    const k = l.niveau in counts ? l.niveau : 'autre';
    counts[k] = (counts[k] ?? 0) + 1;
  }
  return { total: lignes.length, ...counts };
}

export type DroitCell = 'ok' | 'no' | 'lim' | 'own';

export type NiveauDroitUi = 'OK' | 'NO' | 'LIM' | 'OWN';

export const NIVEAUX_DROIT_OPTIONS: { value: NiveauDroitUi; label: string }[] = [
  { value: 'NO', label: '— Aucun' },
  { value: 'OK', label: '✓ Complet' },
  { value: 'LIM', label: 'SON GIE' },
  { value: 'OWN', label: 'LE SIEN' },
];

export function niveauDroitLabel(n: NiveauDroitUi): string {
  return NIVEAUX_DROIT_OPTIONS.find((o) => o.value === n)?.label ?? n;
}

export interface DroitLigne {
  section?: string;
  action?: string;
  superadmin?: DroitCell;
  admin?: DroitCell;
  membre?: DroitCell;
}

export interface JournalEntry {
  icon: string;
  iconBg: string;
  title: string;
  meta: string;
  badge: string;
  badgeClass: string;
}

export const MATRICE_DROITS: DroitLigne[] = [
  { section: '🌐 ORGANISATIONS' },
  { action: 'Voir toutes les organisations', superadmin: 'ok', admin: 'no', membre: 'no' },
  { action: 'Créer / modifier une organisation', superadmin: 'ok', admin: 'no', membre: 'no' },
  { action: 'Suspendre / réactiver une organisation', superadmin: 'ok', admin: 'no', membre: 'no' },
  { section: '👥 MEMBRES & UTILISATEURS' },
  { action: 'Voir tous les membres', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Ajouter / modifier un membre', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Suspendre / exclure un membre', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Changer le poste bureau d\'un membre', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Voir son propre profil', superadmin: 'ok', admin: 'ok', membre: 'ok' },
  { section: '💰 OPÉRATIONS FINANCIÈRES' },
  { action: 'Saisir cotisation / versement / mois', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Accorder un emprunt', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Enregistrer un remboursement', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Appliquer une pénalité / amende', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Enregistrer une dépense', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Opérations bancaires (versement/retrait)', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Annuler / contrepasser une opération', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { section: '🏦 COMPTES & SOLDES' },
  { action: 'Voir les soldes de l\'organisation (Caisse, Banque, Sol.)', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Voir ses propres soldes (5 comptes)', superadmin: 'ok', admin: 'ok', membre: 'own' },
  { action: 'Voir les soldes des autres membres', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { section: '📈 RAPPORTS & EXPORTS' },
  { action: 'Accéder aux rapports complets', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Exporter PDF / Excel', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Voir son propre historique', superadmin: 'ok', admin: 'ok', membre: 'own' },
  { section: '⚙ PARAMÉTRAGE & ADMINISTRATION' },
  { action: 'Configurer les règles comptables', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Gérer les utilisateurs et droits', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Voir le journal des connexions', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Réinitialiser les mots de passe', superadmin: 'ok', admin: 'lim', membre: 'no' },
  { action: 'Configurer la sécurité (2FA, sessions)', superadmin: 'ok', admin: 'lim', membre: 'no' },
];

export const JOURNAL_DEMO: JournalEntry[] = [
  {
    icon: '✅',
    iconBg: 'var(--g3)',
    title: 'Connexion réussie — utilisateur admin',
    meta: 'Journal démo · dernières connexions simulées en attendant l\'audit complet',
    badge: 'Succès',
    badgeClass: 'jr-ok',
  },
  {
    icon: '❌',
    iconBg: 'var(--re2)',
    title: 'Tentative échouée — mot de passe incorrect',
    meta: '3ème tentative · blocage temporaire 30 min',
    badge: 'Échec',
    badgeClass: 'jr-ko',
  },
  {
    icon: '🔑',
    iconBg: 'var(--bl2)',
    title: 'Réinitialisation mot de passe demandée',
    meta: 'Email d\'activation / réinitialisation',
    badge: 'Réinit.',
    badgeClass: 'jr-info',
  },
];

export function cellLabel(cell: DroitCell): string {
  switch (cell) {
    case 'ok':
      return '✓';
    case 'lim':
      return 'SON GIE';
    case 'own':
      return 'LE SIEN';
    default:
      return '—';
  }
}

export function cellClass(cell: DroitCell): string {
  switch (cell) {
    case 'ok':
      return 'chk-ok';
    case 'lim':
      return 'chk-lim';
    case 'own':
      return 'chk-own';
    default:
      return 'chk-no';
  }
}

const AV_COLORS = ['#1a5c3a', '#7c3aed', '#1e6fa8', '#c9922a', '#c0392b', '#2d7a52'];

export function avatarColor(id: number): string {
  return AV_COLORS[id % AV_COLORS.length];
}

export function initials(nomComplet: string): string {
  return nomComplet
    .split(' ')
    .filter(Boolean)
    .map((p) => p[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}
