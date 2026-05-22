export interface SemaineOption {
  value: string;
  label: string;
}

/** Options des N dernières semaines ISO (semaine courante en premier). */
export function buildSemaineOptions(count = 8, refDate = new Date()): SemaineOption[] {
  const out: SemaineOption[] = [];
  const seen = new Set<string>();
  const cursor = new Date(refDate);
  for (let i = 0; i < count * 2 && out.length < count; i++) {
    const { year, week, monday, sunday } = isoWeekBounds(cursor);
    const value = `${year}-W${String(week).padStart(2, '0')}`;
    if (!seen.has(value)) {
      seen.add(value);
      out.push({
        value,
        label: `Semaine ${week} — du ${fmtFr(monday)} au ${fmtFr(sunday)}`,
      });
    }
    cursor.setDate(cursor.getDate() - 7);
  }
  return out;
}

export function semaineCouranteKey(refDate = new Date()): string {
  return semaineOptionForDate(refDate).value;
}

/** Libellé + clé ISO pour une date donnée. */
export function semaineOptionForDate(refDate: Date): SemaineOption {
  const { year, week, monday, sunday } = isoWeekBounds(refDate);
  const value = `${year}-W${String(week).padStart(2, '0')}`;
  return {
    value,
    label: `Semaine ${week} — du ${fmtFr(monday)} au ${fmtFr(sunday)}`,
  };
}

/** Liste des semaines récentes, en incluant toujours la semaine de la date choisie. */
export function buildSemaineOptionsAroundDate(refDate: Date, count = 8): SemaineOption[] {
  const opts = buildSemaineOptions(count, refDate);
  const current = semaineOptionForDate(refDate);
  if (opts.some((o) => o.value === current.value)) {
    return opts;
  }
  return [current, ...opts];
}

export function semaineKeyFromIsoDate(dateIso: string): string | null {
  const d = parseIsoDateLocal(dateIso);
  if (!d) {
    return null;
  }
  return semaineCouranteKey(d);
}

function parseIsoDateLocal(dateIso: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateIso.trim());
  if (!m) {
    return null;
  }
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 12, 0, 0, 0);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** Montants prédéfinis de min à max par pas (défaut 1 000 F). */
export function montantsParPas(min: number, max: number, pas = 1000): number[] {
  const lo = Math.ceil(min / pas) * pas;
  const hi = Math.floor(max / pas) * pas;
  if (lo > hi) return [min];
  const arr: number[] = [];
  for (let m = lo; m <= hi; m += pas) arr.push(m);
  return arr;
}

export function montantDefaut(min: number, max: number, pas = 1000): number {
  const presets = montantsParPas(min, max, pas);
  if (presets.length === 0) return min;
  const milieu = Math.floor(presets.length / 2);
  return presets[milieu];
}

function isoWeekBounds(date: Date): { year: number; week: number; monday: Date; sunday: Date } {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const day = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - day);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
  const monday = new Date(date);
  const dow = date.getDay() || 7;
  monday.setDate(date.getDate() - dow + 1);
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  return { year: d.getUTCFullYear(), week, monday, sunday };
}

function fmtFr(d: Date): string {
  return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
}
