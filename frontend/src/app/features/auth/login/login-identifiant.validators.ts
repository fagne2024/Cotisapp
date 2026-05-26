import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/** Aligné sur `TelephoneUtil.normaliser` côté backend. */
export function normaliserTelephone(raw: string): string | null {
  const digits = raw.replace(/\D/g, '');
  if (!digits) return null;
  let d = digits;
  if (d.startsWith('00')) {
    d = d.substring(2);
  }
  if (d.length === 9 && (d.startsWith('7') || d.startsWith('3'))) {
    d = '221' + d;
  }
  return d;
}

export function estTelephoneValide(raw: string): boolean {
  const n = normaliserTelephone(raw.trim());
  return n != null && n.length >= 9;
}

export function validateurTelephone(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const v = (control.value ?? '').toString().trim();
    if (!v) return null;
    return estTelephoneValide(v) ? null : { telephone: true };
  };
}

const EMAIL_LOGIN_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/i;

export function validateurEmailLogin(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const v = (control.value ?? '').toString().trim();
    if (!v) return null;
    return EMAIL_LOGIN_PATTERN.test(v) ? null : { email: true };
  };
}

export const validateursEmailLogin = [Validators.required, validateurEmailLogin()];
export const validateursTelephoneLogin = [Validators.required, validateurTelephone()];

export type LoginPresetValidator = 'membre' | 'membreSimple' | 'membreBureau' | 'adminGie' | 'super' | null;

export function estConnexionParTelephone(role: LoginPresetValidator): boolean {
  return role === 'membre' || role === 'membreSimple';
}

export type ModeIdentifiantLogin = 'vide' | 'telephone' | 'email';

/** Saisie orientée email (lettres ou @), pas un numéro de téléphone seul. */
export function estSaisieEmailProbable(raw: string): boolean {
  const v = raw.trim();
  if (!v) return false;
  if (v.includes('@')) return true;
  return /[a-zA-ZÀ-ÿ]/.test(v);
}

/** Déduit le mode de connexion à partir de la saisie (téléphone vs email). */
export function detecteModeIdentifiant(raw: string): ModeIdentifiantLogin {
  const v = raw.trim();
  if (!v) return 'vide';
  if (v.includes('@')) return 'email';
  if (estSaisieEmailProbable(v)) return 'vide';
  const digits = v.replace(/\D/g, '');
  if (digits.length >= 9 && /^[\d\s+().-]+$/.test(v)) return 'telephone';
  return 'vide';
}

/** Mode effectif : verrouille l'email dès qu'un @ est présent. */
export function resoutModeIdentifiant(
  raw: string,
  modeActuel: ModeIdentifiantLogin,
  roleEmailSelectionne: boolean
): ModeIdentifiantLogin {
  const v = raw.trim();
  if (!v) return 'vide';
  if (v.includes('@')) return 'email';
  if (roleEmailSelectionne && estSaisieEmailProbable(v)) return 'email';
  const detecte = detecteModeIdentifiant(raw);
  if (modeActuel === 'email' && detecte === 'telephone') {
    return 'email';
  }
  return detecte;
}

export function messageErreurIdentifiant(
  errors: ValidationErrors | null,
  role: LoginPresetValidator
): string | null {
  if (!errors) return null;
  if (errors['required']) {
    return estConnexionParTelephone(role)
      ? 'Le numéro de téléphone est obligatoire.'
      : "L'adresse email est obligatoire.";
  }
  if (errors['email']) {
    return 'Saisissez une adresse email valide (ex. admin@mon-gie.sn).';
  }
  if (errors['telephone']) {
    return 'Saisissez un numéro de téléphone valide (ex. +221 77 123 45 67).';
  }
  return null;
}
