export interface PartCotisationPreset {
  nbParts: number;
  montant: number;
  label: string;
}

export function modePartsActif(regle: {
  montantParPart?: number | null;
  partsMin?: number | null;
  partsMax?: number | null;
} | null): boolean {
  if (!regle?.montantParPart || regle.montantParPart <= 0) return false;
  const pMin = regle.partsMin ?? 0;
  const pMax = regle.partsMax ?? 0;
  return pMax >= pMin && pMin >= 1;
}

export function montantDepuisParts(nbParts: number, montantParPart: number): number {
  return nbParts * montantParPart;
}

export function nombrePartsDepuisMontant(montant: number, montantParPart: number): number | null {
  if (!montantParPart || montantParPart <= 0) return null;
  if (montant % montantParPart !== 0) return null;
  return montant / montantParPart;
}

export function presetsPartsCotisation(
  partsMin: number,
  partsMax: number,
  montantParPart: number
): PartCotisationPreset[] {
  const lo = Math.max(1, partsMin);
  const hi = Math.max(lo, partsMax);
  const out: PartCotisationPreset[] = [];
  for (let p = lo; p <= hi; p++) {
    const montant = montantDepuisParts(p, montantParPart);
    out.push({
      nbParts: p,
      montant,
      label: `${p} part${p > 1 ? 's' : ''} (${formatPartsFcfa(montant)})`,
    });
  }
  return out;
}

export function partDefaut(partsMin: number, partsMax: number): number {
  const lo = Math.max(1, partsMin);
  const hi = Math.max(lo, partsMax);
  return Math.floor((lo + hi) / 2);
}

function formatPartsFcfa(n: number): string {
  return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(n) + ' F';
}

export function plageMontantDepuisParts(
  partsMin: number,
  partsMax: number,
  montantParPart: number
): { min: number; max: number } {
  const vpp = Number(montantParPart);
  const pMin = Math.max(1, Math.floor(Number(partsMin)));
  const pMax = Math.max(pMin, Math.floor(Number(partsMax)));
  return {
    min: montantDepuisParts(pMin, vpp),
    max: montantDepuisParts(pMax, vpp),
  };
}

export interface ResumePlageCotisation {
  montantParPart: number;
  partsMin: number;
  partsMax: number;
  montantMin: number;
  montantMax: number;
}

/** Affichage / enregistrement formulaire : parts × valeur part uniquement. */
export function resumePlageDepuisPartsSaisies(v: {
  montantParPart?: number | null;
  partsMin?: number | null;
  partsMax?: number | null;
}): ResumePlageCotisation | null {
  const vpp = Number(v.montantParPart ?? NaN);
  const pMinRaw = Number(v.partsMin ?? NaN);
  const pMaxRaw = Number(v.partsMax ?? NaN);
  if (!Number.isFinite(vpp) || vpp <= 0) return null;
  if (!Number.isFinite(pMinRaw) || !Number.isFinite(pMaxRaw)) return null;
  const pMin = Math.max(1, Math.floor(pMinRaw));
  const pMax = Math.max(pMin, Math.floor(pMaxRaw));
  const plage = plageMontantDepuisParts(pMin, pMax, vpp);
  return {
    montantParPart: vpp,
    partsMin: pMin,
    partsMax: pMax,
    montantMin: plage.min,
    montantMax: plage.max,
  };
}

/** Chargement API : priorité aux champs parts, sinon déduction depuis montants min/max. */
export function resumePlageDepuisDonneesApi(v: {
  montantParPart?: number | null;
  partsMin?: number | null;
  partsMax?: number | null;
  montantMin?: number | null;
  montantMax?: number | null;
}): ResumePlageCotisation | null {
  const vpp = Number(v.montantParPart ?? NaN);
  if (!Number.isFinite(vpp) || vpp <= 0) return null;

  const pMinRaw = Number(v.partsMin ?? NaN);
  const pMaxRaw = Number(v.partsMax ?? NaN);
  if (Number.isFinite(pMinRaw) && Number.isFinite(pMaxRaw) && pMinRaw >= 1 && pMaxRaw >= pMinRaw) {
    return resumePlageDepuisPartsSaisies({
      montantParPart: vpp,
      partsMin: pMinRaw,
      partsMax: pMaxRaw,
    });
  }

  const mMin = Number(v.montantMin ?? NaN);
  const mMax = Number(v.montantMax ?? NaN);
  if (
    Number.isFinite(mMin) &&
    Number.isFinite(mMax) &&
    mMin > 0 &&
    mMax >= mMin &&
    mMin % vpp === 0 &&
    mMax % vpp === 0
  ) {
    const pMin = mMin / vpp;
    const pMax = mMax / vpp;
    if (pMin >= 1 && pMax >= pMin) {
      return {
        montantParPart: vpp,
        partsMin: pMin,
        partsMax: pMax,
        montantMin: mMin,
        montantMax: mMax,
      };
    }
  }

  return resumePlageDepuisPartsSaisies(v);
}

/** @deprecated Utiliser resumePlageDepuisPartsSaisies ou resumePlageDepuisDonneesApi */
export function resumePlageCotisationDepuisForm(v: {
  montantParPart?: number | null;
  partsMin?: number | null;
  partsMax?: number | null;
  montantMin?: number | null;
  montantMax?: number | null;
}): ResumePlageCotisation | null {
  return resumePlageDepuisPartsSaisies(v) ?? resumePlageDepuisDonneesApi(v);
}
