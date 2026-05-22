import { RegleOperationDto, TypeModeCalcul } from '../../core/services/regle-operation.service';

export interface SimulationEmprunt {
  montantEmprunt: number;
  frais: number;
  totalRembourser: number;
  nbEcheances: number;
  /** Montant des échéances 1 à n−1 (plafonné au max paramétré). */
  montantParEcheance: number;
  /** Dernière échéance (reliquat du total). */
  montantDerniereEcheance: number;
  montantsEcheances: number[];
  /** Date prévue de la dernière échéance (ISO AAAA-MM-JJ). */
  dateDerniereEcheance: string;
  /** Une seule échéance : montant = nominal + frais. */
  paiementUnique: boolean;
  penalite: number;
}

/** Date de la nième échéance (1 = premier mois après octroi). */
export function calculerDateEcheance(
  dateOctroiIso: string,
  numeroEcheance: number,
  jourMois?: number | null
): string {
  if (!dateOctroiIso || numeroEcheance < 1) return '';
  const [y, m, d] = dateOctroiIso.split('-').map(Number);
  const date = new Date(y, m - 1, d);
  date.setMonth(date.getMonth() + numeroEcheance);
  if (jourMois != null && jourMois >= 1 && jourMois <= 31) {
    const maxDay = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
    date.setDate(Math.min(jourMois, maxDay));
  }
  return toIsoDate(date);
}

export function calculerDateDerniereEcheance(
  dateOctroiIso: string,
  nbEcheances: number,
  jourMois?: number | null
): string {
  if (nbEcheances < 1) return dateOctroiIso;
  return calculerDateEcheance(dateOctroiIso, nbEcheances, jourMois);
}

function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * Répartit le total : les (n−1) premières échéances respectent le max (et min) ;
 * la dernière reçoit le reliquat.
 */
export function repartirMontantsEcheances(
  total: number,
  nb: number,
  montantMin?: number | null,
  montantMax?: number | null
): number[] {
  const t = Math.max(0, Math.round(total));
  if (nb <= 0) return [];
  if (nb === 1) return [t];

  const minE = montantMin != null ? Math.max(0, Number(montantMin)) : 0;
  const maxE = montantMax != null ? Math.max(0, Number(montantMax)) : null;

  const montants: number[] = [];
  let reste = t;

  for (let i = 0; i < nb - 1; i++) {
    const slotsApres = nb - i - 1;
    let part: number;
    if (maxE != null) {
      const reserveMin = minE * slotsApres;
      const maxAutorise = Math.max(0, reste - reserveMin);
      part = Math.min(maxE, maxAutorise);
    } else {
      part = Math.ceil(reste / (slotsApres + 1));
    }
    if (minE > 0) {
      part = Math.max(part, Math.min(minE, reste));
    }
    part = Math.max(0, Math.round(part));
    montants.push(part);
    reste -= part;
  }
  montants.push(Math.max(0, reste));
  return montants;
}

export function calculerFrais(
  montant: number,
  typeFrais: TypeModeCalcul | null | undefined,
  montantFrais: number | null | undefined,
  pourcentageFrais: number | null | undefined
): number {
  if (typeFrais === 'POURCENTAGE' && pourcentageFrais != null) {
    return Math.round((montant * Number(pourcentageFrais)) / 100);
  }
  if (typeFrais === 'FIXE' && montantFrais != null) {
    return Number(montantFrais);
  }
  return 0;
}

export function calculerPenalite(
  montant: number,
  typePenalite: TypeModeCalcul | null | undefined,
  montantPenalite: number | null | undefined,
  pourcentagePenalite: number | null | undefined
): number {
  if (typePenalite === 'POURCENTAGE' && pourcentagePenalite != null) {
    return Math.round((montant * Number(pourcentagePenalite)) / 100);
  }
  if (typePenalite === 'FIXE' && montantPenalite != null) {
    return Number(montantPenalite);
  }
  return 0;
}

export function simulerEmpruntDepuisRegle(
  regle: RegleOperationDto | null | undefined,
  montantEmprunt: number,
  nbEcheancesSaisi?: number | null,
  dateOctroiIso?: string | null
): SimulationEmprunt {
  const m = Math.max(0, montantEmprunt);
  const frais = calculerFrais(m, regle?.typeFrais, regle?.montantFrais, regle?.pourcentageFrais);
  const nbMin = regle?.nbEcheancesMin ?? 1;
  const nbMax = regle?.nbEcheancesMax ?? 24;
  const nbDef = regle?.nbEcheancesDefaut ?? nbMin;
  let nb =
    nbEcheancesSaisi != null && nbEcheancesSaisi > 0
      ? Math.floor(nbEcheancesSaisi)
      : nbDef;
  nb = Math.min(nbMax, Math.max(nbMin, nb));
  const total = m + frais;
  const paiementUnique = nb === 1;

  const montantsEcheances = paiementUnique
    ? [total]
    : repartirMontantsEcheances(
        total,
        nb,
        regle?.montantEcheanceMin,
        regle?.montantEcheanceMax
      );
  const parEcheance = montantsEcheances[0] ?? total;
  const derniereEcheance = montantsEcheances[montantsEcheances.length - 1] ?? total;

  const penalite = calculerPenalite(
    m,
    regle?.typePenalite,
    regle?.montantPenalite,
    regle?.pourcentagePenalite
  );

  const dateOctroi = dateOctroiIso?.trim() || toIsoDate(new Date());
  const dateDerniereEcheance = calculerDateDerniereEcheance(
    dateOctroi,
    nb,
    regle?.jourEcheanceMois
  );

  return {
    montantEmprunt: m,
    frais,
    totalRembourser: total,
    nbEcheances: nb,
    montantParEcheance: parEcheance,
    montantDerniereEcheance: derniereEcheance,
    montantsEcheances,
    dateDerniereEcheance,
    paiementUnique,
    penalite,
  };
}

/** Total à rembourser (capital + frais) pour un montant emprunté donné. */
export function totalRembourserPourCapital(
  capital: number,
  regle: RegleOperationDto | null | undefined
): number {
  const m = Math.max(0, capital);
  return m + calculerFrais(m, regle?.typeFrais, regle?.montantFrais, regle?.pourcentageFrais);
}

/** Plage d'échéance (paiement unique) dérivée des montants min/max emprunt + frais. */
export function plageEcheanceDepuisMontantsEmprunt(
  regle: RegleOperationDto | null | undefined,
  montantMin: number | null | undefined,
  montantMax: number | null | undefined
): { min: number; max: number } | null {
  if (montantMin == null && montantMax == null) {
    return null;
  }
  const capMin = Math.max(0, Number(montantMin ?? montantMax ?? 0));
  const capMax = Math.max(capMin, Math.max(0, Number(montantMax ?? montantMin ?? capMin)));
  return {
    min: totalRembourserPourCapital(capMin, regle),
    max: totalRembourserPourCapital(capMax, regle),
  };
}

/** Montant à avancer depuis la caisse lorsque le solde solidarité est insuffisant. */
export function calculerAvanceCaisseVersSolidarite(
  soldeSolidarite: number,
  debitTotal: number
): number {
  const disponible = Math.max(0, soldeSolidarite);
  return Math.max(0, debitTotal - disponible);
}

/** Indique si la règle autorise le paiement en une fois (nominal + frais). */
export function reglePaiementUniquePossible(regle: RegleOperationDto | null | undefined): boolean {
  if (!regle) return false;
  const min = regle.nbEcheancesMin ?? 1;
  const max = regle.nbEcheancesMax ?? min;
  return min === 1 || max === 1 || (regle.nbEcheancesDefaut ?? min) === 1;
}
