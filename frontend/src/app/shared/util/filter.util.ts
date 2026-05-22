/** Filtre texte insensible à la casse sur une ou plusieurs chaînes. */
export function matchTextQuery(query: string, ...fields: (string | undefined | null)[]): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return fields.some((f) => (f ?? '').toLowerCase().includes(q));
}
