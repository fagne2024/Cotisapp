/** Regroupement affiché sur l'écran de connexion. */
export type LoginGroupe = 'admin' | 'membre';

/** Sous-type de compte (après choix ADMIN ou MEMBRE). */
export type LoginPreset = 'super' | 'adminGie' | 'membreSimple' | 'membreBureau';

export function groupePourPreset(role: LoginPreset): LoginGroupe {
  return role === 'super' || role === 'adminGie' ? 'admin' : 'membre';
}

export function libelleLoginGroupe(groupe: LoginGroupe | null): string {
  if (groupe === 'admin') return 'ADMIN';
  if (groupe === 'membre') return 'MEMBRE';
  return '';
}

export function estMembreSimple(role: LoginPreset | null): boolean {
  return role === 'membreSimple';
}

export function estMembreBureau(role: LoginPreset | null): boolean {
  return role === 'membreBureau';
}

export function estConnexionEmail(role: LoginPreset | null): boolean {
  return role === 'super' || role === 'adminGie' || role === 'membreBureau';
}

export function libelleLoginPreset(role: LoginPreset | null): string {
  switch (role) {
    case 'super':
      return 'Superadmin';
    case 'adminGie':
      return 'Admin GIE';
    case 'membreSimple':
      return 'Membre simple';
    case 'membreBureau':
      return 'Membre de bureau';
    default:
      return '';
  }
}

export function libelleSousTypeAttendu(groupe: LoginGroupe | null): string {
  if (groupe === 'admin') return 'Admin GIE ou Superadmin';
  if (groupe === 'membre') return 'Membre simple ou Membre de bureau';
  return 'un sous-type';
}

/** Rôles proposés lorsque l'identifiant est une adresse email. */
export const PRESETS_CONNEXION_EMAIL: LoginPreset[] = ['membreBureau', 'adminGie', 'super'];
