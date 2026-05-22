/** Champs minimaux pour la recherche membre (cotisation, etc.). */
export interface MembreRecherche {
  id: number;
  codeMembre: string;
  nom: string;
  prenom: string;
  nomComplet: string;
  telephone?: string | null;
}

export function matchMembreQuery(m: MembreRecherche, rawQuery: string): boolean {
  const q = rawQuery.trim().toLowerCase();
  if (!q) return false;
  const tel = (m.telephone ?? '').replace(/\s/g, '');
  const qTel = q.replace(/\s/g, '');
  return (
    m.codeMembre.toLowerCase().includes(q) ||
    m.nom.toLowerCase().includes(q) ||
    m.prenom.toLowerCase().includes(q) ||
    m.nomComplet.toLowerCase().includes(q) ||
    (qTel.length > 0 && tel.includes(qTel))
  );
}

export function filtrerMembres<T extends MembreRecherche>(list: T[], rawQuery: string): T[] {
  const q = rawQuery.trim();
  if (!q) return [];
  return list.filter((m) => matchMembreQuery(m, q));
}
