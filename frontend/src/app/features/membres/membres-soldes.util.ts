import { MembreSoldesDto } from '../../core/services/membre.service';

export interface SoldesMembreLigne {
  epargneHebdo: number;
  epargneMois: number;
  solidarite: number;
  penalite: number;
  amende: number;
}

export const SOLDES_VIDES: SoldesMembreLigne = {
  epargneHebdo: 0,
  epargneMois: 0,
  solidarite: 0,
  penalite: 0,
  amende: 0,
};

export function soldesDepuisApi(dto?: MembreSoldesDto | null): SoldesMembreLigne {
  if (!dto) return { ...SOLDES_VIDES };
  return {
    epargneHebdo: Number(dto.epargneHebdo ?? 0),
    epargneMois: Number(dto.epargneMois ?? 0),
    solidarite: Number(dto.solidarite ?? 0),
    penalite: Number(dto.penalite ?? 0),
    amende: Number(dto.amende ?? 0),
  };
}

export function mapSoldesParMembre(soldes: MembreSoldesDto[]): Map<number, SoldesMembreLigne> {
  const map = new Map<number, SoldesMembreLigne>();
  for (const s of soldes) {
    map.set(s.membreId, soldesDepuisApi(s));
  }
  return map;
}
