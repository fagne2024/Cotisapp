import { RecapCompteDto, RecapMembreDto, RecapOperationLigneDto } from '../../core/services/recap-journee.service';

export function initialsFromName(nom: string): string {
  const parts = nom.trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function planadBadgeFromLibelle(libelle: string): string {
  const m = libelle.match(/^([^\s]+)/);
  return m ? m[1].toUpperCase() : 'PLANAD';
}

export function soldeDebutCompte(c: RecapCompteDto): number {
  return c.soldeFinJournee - c.variationJour;
}

export function typeOperationMeta(type: string): { icon: string; bg: string } {
  switch (type) {
    case 'COTISATION':
    case 'VERSEMENT':
      return { icon: '💰', bg: 'var(--g3)' };
    case 'COTISATION_MOIS':
      return { icon: '📅', bg: 'var(--or3)' };
    case 'EMPRUNT':
      return { icon: '📋', bg: 'var(--re2)' };
    case 'REMBOURSEMENT':
      return { icon: '🔄', bg: 'var(--bl2)' };
    default:
      return { icon: '•', bg: '#f1f0eb' };
  }
}

export function compteOrgBoxClass(type: string): string {
  switch (type?.toUpperCase()) {
    case 'CAISSE':
      return 'cb-caisse';
    case 'SOLIDARITE':
      return 'cb-sol';
    case 'BANQUE':
      return 'cb-banque';
    default:
      return 'cb-caisse';
  }
}

export function filtrerParRechercheGlobale<T extends { membreNom?: string | null; codeMembre?: string | null }>(
  items: T[],
  query: string
): T[] {
  const q = query.trim().toLowerCase();
  if (!q) return items;
  return items.filter((item) => {
    const nom = item.membreNom?.toLowerCase() ?? '';
    const code = item.codeMembre?.toLowerCase() ?? '';
    return nom.includes(q) || code.includes(q);
  });
}

export function filtrerOperationsAvance(
  ops: RecapOperationLigneDto[],
  typeFiltre: string,
  statutFiltre: string
): RecapOperationLigneDto[] {
  return ops.filter((op) => {
    if (typeFiltre) {
      if (typeFiltre === 'COTISATION' && !['COTISATION', 'COTISATION_MOIS', 'VERSEMENT'].includes(op.typeOperation)) {
        return false;
      }
      if (typeFiltre === 'EMPRUNT' && op.typeOperation !== 'EMPRUNT') return false;
      if (typeFiltre === 'REMBOURSEMENT' && op.typeOperation !== 'REMBOURSEMENT') return false;
    }
    if (statutFiltre === 'ACTIVE' && (op.annulee || op.annulation)) return false;
    if (statutFiltre === 'ANNULEE' && !op.annulee) return false;
    if (statutFiltre === 'ANNULATION' && !op.annulation) return false;
    return true;
  });
}

export function avatarColor(seed: string): string {
  const colors = ['#2d7a52', '#7c3aed', '#c0392b', '#c9922a', '#1a5c3a', '#1e6fa8'];
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h + seed.charCodeAt(i)) % colors.length;
  return colors[h];
}

export function statutMembreLabel(m: RecapMembreDto): { label: string; class: string } {
  if (m.montantCotisations > 0 && m.variationNetComptes >= 0) {
    return { label: '✓ À jour', class: 'b-green' };
  }
  if (m.variationNetComptes < 0 && m.montantEmprunts > 0) {
    return { label: '⚠ Retard', class: 'b-red' };
  }
  return { label: 'Actif', class: 'b-gray' };
}
