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

export const validateursEmailLogin = [Validators.required, Validators.email];
export const validateursTelephoneLogin = [Validators.required, validateurTelephone()];

export type LoginPresetValidator = 'membre' | 'membreSimple' | 'membreBureau' | 'adminGie' | 'super' | null;

export function estConnexionParTelephone(role: LoginPresetValidator): boolean {
  return role === 'membre' || role === 'membreSimple';
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
