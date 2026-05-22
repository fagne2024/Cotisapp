import {
  CompteReleveDto,
  ReleveGroupeDto,
  ReleveLigneDto,
  ReleveTotauxDto,
} from '../../core/services/compte-releve.service';

function n(v: number | string | null | undefined): number {
  if (v == null || v === '') return 0;
  return typeof v === 'number' ? v : parseFloat(String(v));
}

function correspondTypeFiltre(typeOp: string, filtre: string): boolean {
  switch (filtre) {
    case 'cotisation':
      return typeOp === 'COTISATION';
    case 'mois':
      return typeOp === 'COTISATION_MOIS';
    case 'versement':
      return typeOp === 'VERSEMENT' || typeOp.startsWith('BANQUE');
    case 'emprunt':
      return typeOp === 'EMPRUNT';
    case 'remboursement':
      return typeOp === 'REMBOURSEMENT';
    case 'penalite':
      return typeOp === 'PENALITE';
    case 'amende':
      return typeOp === 'AMENDE';
    case 'depense':
      return typeOp === 'DEPENSE';
    case 'banque':
      return typeOp.startsWith('BANQUE');
    default:
      return true;
  }
}

export function passeFiltreLigne(
  l: ReleveLigneDto,
  typeFiltre: string,
  statutFiltre: string,
  recherche: string
): boolean {
  if (typeFiltre) {
    if (!correspondTypeFiltre(l.typeOperation, typeFiltre.toLowerCase())) {
      return false;
    }
  }
  if (statutFiltre === 'active' && l.annulee) return false;
  if (statutFiltre === 'annulee' && !l.annulee) return false;
  const q = recherche.trim().toLowerCase();
  if (q) {
    const blob = `${l.titre} ${l.membreNom ?? ''} ${l.codeMembre ?? ''} ${l.reference ?? ''} ${l.metaExtra ?? ''}`.toLowerCase();
    if (!blob.includes(q)) return false;
  }
  return true;
}

function calculerTotaux(lignes: ReleveLigneDto[]): ReleveTotauxDto {
  let entrees = 0;
  let sorties = 0;
  let annulees = 0;
  for (const l of lignes) {
    if (l.annulee || l.contrepassation) {
      annulees++;
      continue;
    }
    const m = n(l.montant);
    if (l.sens === 'credit') entrees += m;
    else sorties += m;
  }
  return {
    entrees,
    sorties,
    variationNette: entrees - sorties,
    nbOperations: lignes.length,
    nbAnnulees: annulees,
  };
}

/** Filtre type / statut / recherche côté client (pas d’appel API). */
export function appliquerFiltresReleve(
  releve: CompteReleveDto,
  typeFiltre: string,
  statutFiltre: string,
  recherche: string
): CompteReleveDto {
  const groupes: ReleveGroupeDto[] = [];
  const toutesLignes: ReleveLigneDto[] = [];

  for (const g of releve.groupes) {
    const lignes = g.lignes.filter((l) => passeFiltreLigne(l, typeFiltre, statutFiltre, recherche));
    if (lignes.length) {
      groupes.push({ ...g, lignes });
      toutesLignes.push(...lignes);
    }
  }

  return {
    ...releve,
    groupes,
    totaux: calculerTotaux(toutesLignes),
  };
}

/** Découpe les lignes du relevé par page, puis regroupe par jour pour l'affichage. */
export function paginerReleveGroupes(
  groupes: ReleveGroupeDto[],
  page: number,
  pageSize: number
): { groupes: ReleveGroupeDto[]; totalLignes: number } {
  const flat: { groupe: ReleveGroupeDto; ligne: ReleveLigneDto }[] = [];
  for (const g of groupes) {
    for (const l of g.lignes) {
      flat.push({ groupe: g, ligne: l });
    }
  }
  const totalLignes = flat.length;
  if (totalLignes === 0) {
    return { groupes: [], totalLignes: 0 };
  }
  const start = (page - 1) * pageSize;
  const sliced = flat.slice(start, start + pageSize);
  const parDate = new Map<string, ReleveGroupeDto>();
  const ordreDates: string[] = [];
  for (const { groupe, ligne } of sliced) {
    if (!parDate.has(groupe.date)) {
      parDate.set(groupe.date, { date: groupe.date, label: groupe.label, lignes: [] });
      ordreDates.push(groupe.date);
    }
    parDate.get(groupe.date)!.lignes.push(ligne);
  }
  return {
    groupes: ordreDates.map((d) => parDate.get(d)!),
    totalLignes,
  };
}
