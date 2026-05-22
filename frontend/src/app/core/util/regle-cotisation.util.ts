import { RegleOperationDto } from '../services/regle-operation.service';
import { resumePlageDepuisDonneesApi } from '../../shared/util/parts-cotisation.util';

export interface RegleCotisationUi {
  id?: number;
  libelle: string;
  periodicite: string;
  montantMin: number;
  montantMax: number;
  montantParPart?: number | null;
  partsMin?: number | null;
  partsMax?: number | null;
  solidariteAuto: boolean;
  montantSolidarite: number;
  montantAmendeMin: number;
  montantAmendeMax: number;
  actif: boolean;
}

const PERIODICITE_LABEL: Record<string, string> = {
  HEBDOMADAIRE: 'Hebdomadaire',
  MENSUEL: 'Mensuelle',
  LIBRE: 'Libre',
};

export function regleCotisationDepuisDto(
  dto: RegleOperationDto | null | undefined,
  fallback: RegleCotisationUi
): RegleCotisationUi {
  if (!dto) return { ...fallback };
  const resume = resumePlageDepuisDonneesApi({
    montantParPart: dto.montantParPart,
    partsMin: dto.partsMin,
    partsMax: dto.partsMax,
    montantMin: dto.montantMin,
    montantMax: dto.montantMax,
  });
  return {
    id: dto.id,
    libelle: dto.libelle,
    periodicite: dto.periodicite ? (PERIODICITE_LABEL[dto.periodicite] ?? dto.periodicite) : '—',
    montantMin: resume ? resume.montantMin : Number(dto.montantMin ?? fallback.montantMin),
    montantMax: resume ? resume.montantMax : Number(dto.montantMax ?? fallback.montantMax),
    montantParPart: resume ? resume.montantParPart : (dto.montantParPart != null ? Number(dto.montantParPart) : fallback.montantParPart),
    partsMin: resume ? resume.partsMin : (dto.partsMin ?? fallback.partsMin),
    partsMax: resume ? resume.partsMax : (dto.partsMax ?? fallback.partsMax),
    solidariteAuto: dto.solidariteAuto,
    montantSolidarite: Number(dto.montantSolidariteAuto ?? fallback.montantSolidarite),
    montantAmendeMin: Number(dto.montantAmendeMin ?? fallback.montantAmendeMin),
    montantAmendeMax: Number(dto.montantAmendeMax ?? fallback.montantAmendeMax),
    actif: dto.actif,
  };
}

export const REGLE_HEBDO_FALLBACK: RegleCotisationUi = {
  libelle: 'Cotisation Hebdomadaire',
  periodicite: 'Hebdomadaire',
  montantParPart: 1000,
  partsMin: 1,
  partsMax: 10,
  montantMin: 1000,
  montantMax: 10000,
  solidariteAuto: true,
  montantSolidarite: 200,
  montantAmendeMin: 500,
  montantAmendeMax: 3000,
  actif: true,
};

export const REGLE_MOIS_FALLBACK: RegleCotisationUi = {
  libelle: 'Cotisation Mensuelle (Mois)',
  periodicite: 'Mensuelle',
  montantParPart: 1000,
  partsMin: 5,
  partsMax: 20,
  montantMin: 5000,
  montantMax: 20000,
  solidariteAuto: true,
  montantSolidarite: 200,
  montantAmendeMin: 1000,
  montantAmendeMax: 5000,
  actif: true,
};
