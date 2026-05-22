import { matchTextQuery } from '../../shared/util/filter.util';
import { numeroCorrespondAuCode } from '../../shared/util/membre-code-lookup.util';
import {
  JourneeReunionDto,
  RecapMembreDto,
  RecapOperationLigneDto,
} from '../../core/services/recap-journee.service';

export interface FiltreMembreRecap {
  texte: string;
  codeNumero: string;
}

export function filtreMembreRecapActif(f: FiltreMembreRecap): boolean {
  return !!(f.texte.trim() || f.codeNumero.trim());
}

export function filtrePlanadNumeroActif(numeroQuery: string): boolean {
  return !!numeroQuery.trim();
}

export function filtrerJourneesParNumero(
  list: JourneeReunionDto[],
  numeroQuery: string
): JourneeReunionDto[] {
  const q = numeroQuery.trim();
  if (!q) return list;
  const n = parseInt(q, 10);
  if (!Number.isNaN(n)) {
    return list.filter((j) => j.numero === n);
  }
  const lower = q.toLowerCase();
  return list.filter((j) => j.libelle.toLowerCase().includes(lower));
}

export function filtrerRecapMembres(list: RecapMembreDto[], f: FiltreMembreRecap): RecapMembreDto[] {
  if (!filtreMembreRecapActif(f)) return list;
  return list.filter((m) => correspondFiltreMembre(m.codeMembre, m.membreNom, f));
}

export function filtrerRecapOperations(
  list: RecapOperationLigneDto[],
  f: FiltreMembreRecap
): RecapOperationLigneDto[] {
  if (!filtreMembreRecapActif(f)) return list;
  return list.filter((op) => {
    if (!op.membreId && !op.codeMembre && !op.membreNom) return false;
    return correspondFiltreMembre(op.codeMembre ?? '', op.membreNom ?? '', f);
  });
}

function correspondFiltreMembre(codeMembre: string, nom: string, f: FiltreMembreRecap): boolean {
  const codeNum = f.codeNumero.trim();
  if (codeNum && !numeroCorrespondAuCode(codeMembre, codeNum)) return false;
  return matchTextQuery(f.texte, codeMembre, nom);
}
