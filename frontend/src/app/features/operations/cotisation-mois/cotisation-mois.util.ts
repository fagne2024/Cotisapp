export interface MoisOption {
  value: string;
  label: string;
}

/** Options des N derniers mois (mois courant en premier). */
export function buildMoisOptions(count = 6, refDate = new Date()): MoisOption[] {
  const out: MoisOption[] = [];
  const cursor = new Date(refDate.getFullYear(), refDate.getMonth(), 1);
  for (let i = 0; i < count; i++) {
    const y = cursor.getFullYear();
    const m = cursor.getMonth() + 1;
    const value = `${y}-${String(m).padStart(2, '0')}`;
    const label = cursor.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
    out.push({
      value,
      label: label.charAt(0).toUpperCase() + label.slice(1),
    });
    cursor.setMonth(cursor.getMonth() - 1);
  }
  return out;
}

export function moisCourantKey(refDate = new Date()): string {
  const y = refDate.getFullYear();
  const m = refDate.getMonth() + 1;
  return `${y}-${String(m).padStart(2, '0')}`;
}
