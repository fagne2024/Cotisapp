import { MembreDto } from '../../core/services/membre.service';

/** Partie numérique après le dernier tiret (ex. PLANAD-001 → 001). */
export function suffixeCodeNumerique(code: string): string {
  const c = (code ?? '').trim();
  const i = c.lastIndexOf('-');
  return i >= 0 ? c.slice(i + 1) : c;
}

/** Compare un numéro saisi au code membre (suffixe ou code complet). */
export function numeroCorrespondAuCode(codeMembre: string, numero: string): boolean {
  const n = (numero ?? '').trim();
  if (!n) return false;
  const code = (codeMembre ?? '').trim().toUpperCase();
  const nUp = n.toUpperCase();
  if (code === nUp) return true;
  if (code.endsWith('-' + nUp)) return true;
  const suffix = suffixeCodeNumerique(codeMembre).toUpperCase();
  if (suffix === nUp) return true;
  const stripZeros = (s: string) => s.replace(/^0+/, '') || '0';
  return stripZeros(suffix) === stripZeros(nUp);
}

export function filtrerMembresParNumeroCode(membres: MembreDto[], numero: string): MembreDto[] {
  const n = (numero ?? '').trim();
  if (!n) return [];
  return membres.filter((m) => numeroCorrespondAuCode(m.codeMembre, n));
}

/** Filtre toute liste portant un code membre (ex. emprunts en cours). */
export function filtrerParNumeroCode<T extends { codeMembre: string }>(items: T[], numero: string): T[] {
  const n = (numero ?? '').trim();
  if (!n) return [];
  return items.filter((i) => numeroCorrespondAuCode(i.codeMembre, n));
}
