import { MouvementPreview } from '../../../core/services/operation.service';

/** Fusionne épargne membre + caisse org si même montant (ancienne API ou double ligne). */
export function normaliserPreviewCotisation(
  lignes: MouvementPreview[],
  nomMembre?: string | null
): MouvementPreview[] {
  if (lignes.length === 0) return [];

  const isEpargneMembre = (l: MouvementPreview) =>
    /membre/i.test(l.libelle) && /épargne|epargne/i.test(l.libelle);
  const isCaisseOrg = (l: MouvementPreview) =>
    /organisation/i.test(l.libelle) && /caisse/i.test(l.libelle);
  const isCotisationFusionnee = (l: MouvementPreview) =>
    /cotisation/i.test(l.libelle) && /caisse organisation/i.test(l.libelle);

  if (lignes.some(isCotisationFusionnee)) {
    return lignes;
  }

  const epargne = lignes.find(isEpargneMembre);
  const caisse = lignes.find(isCaisseOrg);
  const autres = lignes.filter((l) => l !== epargne && l !== caisse);

  if (epargne && caisse && Number(epargne.montant) === Number(caisse.montant)) {
    const m = Number(epargne.montant);
    const nom =
      nomMembre?.trim() ||
      extraireNomDepuisLibelle(epargne.libelle) ||
      'membre';
    return [
      {
        libelle: `Cotisation — Épargne (${nom}) et Caisse organisation`,
        sens: 'CREDIT',
        montant: m,
      },
      ...autres,
    ];
  }

  return lignes;
}

export function totalCreditPreview(lignes: MouvementPreview[]): number {
  return lignes.reduce((s, l) => s + Number(l.montant ?? 0), 0);
}

function extraireNomDepuisLibelle(libelle: string): string | null {
  const m = libelle.match(/\(([^)]+)\)/);
  return m?.[1]?.trim() ?? null;
}
