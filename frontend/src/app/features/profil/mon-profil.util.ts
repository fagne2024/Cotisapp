export interface ProfilSessionUi {
  id: string;
  icon: string;
  iconBg: string;
  device: string;
  meta: string;
  current: boolean;
  online: boolean;
}

export interface ProfilActiviteUi {
  icon: string;
  iconBg: string;
  title: string;
  date: string;
}

export const SESSIONS_DEMO: ProfilSessionUi[] = [
  {
    id: 'current',
    icon: '💻',
    iconBg: 'var(--g3)',
    device: 'Navigateur — session actuelle',
    meta: 'Session en cours · CotisApp web',
    current: true,
    online: true,
  },
];

export const ACTIVITE_DEMO: ProfilActiviteUi[] = [
  {
    icon: '👤',
    iconBg: 'var(--g3)',
    title: 'Connexion à CotisApp',
    date: "Aujourd'hui",
  },
  {
    icon: '🔐',
    iconBg: 'var(--bl2)',
    title: 'Consultation Utilisateurs & Droits',
    date: 'Récemment',
  },
];

export type PwdStrength = 'none' | 'weak' | 'medium' | 'strong';

export function evaluerForceMotDePasse(val: string): { score: number; level: PwdStrength; label: string } {
  if (!val.length) {
    return { score: 0, level: 'none', label: 'Saisissez un mot de passe' };
  }
  let score = 0;
  if (val.length >= 8) score++;
  if (/[A-Z]/.test(val)) score++;
  if (/[0-9]/.test(val)) score++;
  if (/[^A-Za-z0-9]/.test(val)) score++;
  const levels: PwdStrength[] = ['weak', 'weak', 'medium', 'strong', 'strong'];
  const labels = ['Trop court', 'Faible', 'Moyen', 'Fort', 'Très fort'];
  return { score, level: levels[score], label: labels[score] };
}

export function initialsFromNom(prenom: string, nom: string): string {
  const a = (prenom[0] ?? '').toUpperCase();
  const b = (nom[0] ?? '').toUpperCase();
  return (a + b) || '?';
}
