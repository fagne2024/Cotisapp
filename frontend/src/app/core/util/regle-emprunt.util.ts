import { EmpruntsReglesDto, RegleOperationDto } from '../services/regle-operation.service';

export type EmpruntTypeUi = 'etale' | 'caisse' | 'sol';

export function reglePourTypeUi(
  regles: EmpruntsReglesDto | null | undefined,
  type: EmpruntTypeUi
): RegleOperationDto | null {
  if (!regles) return null;
  switch (type) {
    case 'etale':
      return regles.etale;
    case 'caisse':
      return regles.caisse;
    case 'sol':
      return regles.solidarite;
  }
}

export const REGLE_EMPRUNT_ETALE_FALLBACK: RegleOperationDto = {
  id: 0,
  typeOperation: 'EMPRUNT',
  libelle: 'Emprunt Étalé / Financement',
  periodicite: null,
  montantMin: 25_000,
  montantMax: 500_000,
  solidariteAuto: false,
  montantSolidariteAuto: null,
  typeFrais: 'POURCENTAGE',
  pourcentageFrais: 5,
  montantFrais: null,
  nbEcheancesMin: 3,
  nbEcheancesMax: 12,
  nbEcheancesDefaut: 4,
  montantEcheanceMin: 5_000,
  montantEcheanceMax: 150_000,
  typePenalite: 'FIXE',
  montantPenalite: 500,
  pourcentagePenalite: null,
  actif: true,
  mouvements: [],
};

export const REGLE_EMPRUNT_CAISSE_FALLBACK: RegleOperationDto = {
  id: 0,
  typeOperation: 'EMPRUNT',
  libelle: 'Emprunt Caisse',
  periodicite: null,
  montantMin: 50_000,
  montantMax: 1_000_000,
  solidariteAuto: false,
  montantSolidariteAuto: null,
  typeFrais: 'FIXE',
  montantFrais: 5_000,
  pourcentageFrais: null,
  nbEcheancesMin: 1,
  nbEcheancesMax: 6,
  nbEcheancesDefaut: 3,
  montantEcheanceMin: 10_000,
  montantEcheanceMax: 400_000,
  typePenalite: 'POURCENTAGE',
  montantPenalite: null,
  pourcentagePenalite: 2,
  actif: true,
  mouvements: [],
};

export const REGLE_EMPRUNT_SOLIDARITE_FALLBACK: RegleOperationDto = {
  id: 0,
  typeOperation: 'EMPRUNT',
  libelle: 'Emprunt Solidarité',
  periodicite: null,
  montantMin: 5_000,
  montantMax: 150_000,
  solidariteAuto: false,
  montantSolidariteAuto: null,
  typeFrais: null,
  montantFrais: null,
  pourcentageFrais: null,
  nbEcheancesMin: 1,
  nbEcheancesMax: 1,
  nbEcheancesDefaut: 1,
  montantEcheanceMin: 5_000,
  montantEcheanceMax: 150_000,
  typePenalite: 'FIXE',
  montantPenalite: 200,
  pourcentagePenalite: null,
  actif: true,
  mouvements: [],
};

export function regleEmpruntEffective(
  regles: EmpruntsReglesDto | null | undefined,
  type: EmpruntTypeUi
): RegleOperationDto {
  const dto = reglePourTypeUi(regles, type);
  if (dto) return dto;
  switch (type) {
    case 'caisse':
      return REGLE_EMPRUNT_CAISSE_FALLBACK;
    case 'sol':
      return REGLE_EMPRUNT_SOLIDARITE_FALLBACK;
    default:
      return REGLE_EMPRUNT_ETALE_FALLBACK;
  }
}

/** Règle sans frais d'octroi (type absent ou montant / % à zéro). */
export function empruntSansFrais(regle: RegleOperationDto | null | undefined): boolean {
  if (!regle?.typeFrais) {
    return true;
  }
  if (regle.typeFrais === 'FIXE') {
    return regle.montantFrais == null || Number(regle.montantFrais) === 0;
  }
  if (regle.typeFrais === 'POURCENTAGE') {
    return regle.pourcentageFrais == null || Number(regle.pourcentageFrais) === 0;
  }
  return true;
}

export function libelleFraisEmprunt(regle: RegleOperationDto): string {
  if (empruntSansFrais(regle)) {
    return 'Aucun frais';
  }
  if (regle.typeFrais === 'POURCENTAGE' && regle.pourcentageFrais != null) {
    return `Frais (${regle.pourcentageFrais} % du capital)`;
  }
  if (regle.typeFrais === 'FIXE' && regle.montantFrais != null) {
    return `Frais fixes (${regle.montantFrais} F)`;
  }
  return 'Aucun frais';
}

export function libellePenaliteEmprunt(regle: RegleOperationDto): string {
  if (regle.typePenalite === 'POURCENTAGE' && regle.pourcentagePenalite != null) {
    return `Pénalité retard : ${regle.pourcentagePenalite}%`;
  }
  if (regle.typePenalite === 'FIXE' && regle.montantPenalite != null) {
    return `Pénalité retard : ${regle.montantPenalite} F`;
  }
  return 'Pénalité retard';
}
