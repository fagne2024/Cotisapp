import {
  MembreRepartitionClotureDto,
  ModeRepartitionCloture,
  PostePartageClotureDto,
  PreviewClotureExerciceDto,
} from '../../core/services/parametrage-cloture.service';

export function libelleModeRepartition(mode: ModeRepartitionCloture | string | undefined): string {
  if (mode === 'EQUITABLE') return 'Partage équitable';
  return 'Prorata';
}

export function libelleAgregationPostes(mode: string | undefined): string {
  switch (mode) {
    case 'ADDITIONNER':
      return 'Addition au choix + postes séparés';
    case 'GROUPES':
      return 'Deux groupes';
    default:
      return 'Postes séparés';
  }
}

export function postesActifsPreview(preview: PreviewClotureExerciceDto): PostePartageClotureDto[] {
  return (preview.postes ?? []).filter((p) => p.actif);
}

/** Colonnes du tableau membre selon le mode d'agrégation. */
export function colonnesDistributionPreview(preview: PreviewClotureExerciceDto): { code: string; libelle: string }[] {
  const agreg = preview.modeAgregationPostes ?? 'SEPARER';
  if (agreg === 'ADDITIONNER') {
    const cols: { code: string; libelle: string }[] = [];
    const actifs = postesActifsPreview(preview);
    if (actifs.some((p) => p.inclureDansPoolAdditionne)) {
      cols.push({ code: '__POOL__', libelle: 'Montants additionnés' });
    }
    for (const p of actifs.filter((x) => !x.inclureDansPoolAdditionne)) {
      cols.push({ code: p.code, libelle: p.libelle });
    }
    return cols;
  }
  if (agreg === 'GROUPES') {
    return [
      { code: 'GROUPE_1', libelle: 'Groupe 1' },
      { code: 'GROUPE_2', libelle: 'Groupe 2' },
    ];
  }
  return postesActifsPreview(preview).map((p) => ({ code: p.code, libelle: p.libelle }));
}

export function montantPosteMembre(m: MembreRepartitionClotureDto, code: string): number {
  const map = m.montantsParPoste;
  if (map && map[code] != null) {
    return Number(map[code]) || 0;
  }
  switch (code) {
    case 'INTERETS':
      return m.montantInterets ?? 0;
    case 'PENALITES':
      return m.montantPenalites ?? 0;
    case 'AMENDES':
      return m.montantAmendes ?? 0;
    default:
      return 0;
  }
}

export function libelleCompteCourt(type: string | undefined): string {
  switch (type) {
    case 'INTERET':
      return 'Intérêts';
    case 'PENALITE':
      return 'Pénalité';
    case 'AMENDE':
      return 'Amende';
    case 'EPARGNE_HEBDO':
      return 'Ép. hebdo';
    case 'EPARGNE_MOIS':
      return 'Ép. mois';
    case 'CAISSE':
      return 'Caisse';
    case 'SOLIDARITE':
      return 'Solidarité';
    case 'BANQUE':
      return 'Banque';
    default:
      return type ?? '—';
  }
}
