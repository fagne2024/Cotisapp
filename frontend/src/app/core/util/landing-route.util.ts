import { MesDroitsDto } from '../services/droit-acces.service';

const LANDING_CANDIDATES: { action: string; segments: string[] }[] = [
  { action: 'MEMBRE_LISTER', segments: ['membres'] },
  { action: 'OP_COTISATION', segments: ['operations', 'cotisation-mois'] },
  { action: 'OP_EMPRUNT', segments: ['operations', 'emprunts'] },
  { action: 'OP_REMBOURSEMENT', segments: ['operations', 'remboursements'] },
  { action: 'OP_PENALITE', segments: ['operations', 'penalite-amende'] },
  { action: 'OP_DEPENSE', segments: ['gestion', 'tresorerie'] },
  { action: 'OP_BANQUE', segments: ['gestion', 'tresorerie'] },
  { action: 'SOLDE_ORG', segments: ['gestion', 'comptes'] },
  { action: 'RAPPORT_COMPLET', segments: ['rapports'] },
  { action: 'PARAM_REGLES', segments: ['parametrage', 'regles'] },
  { action: 'ADMIN_UTILISATEURS', segments: ['gestion', 'utilisateurs'] },
];

function peut(droits: MesDroitsDto | null, code: string): boolean {
  const n = droits?.actions?.[code];
  return n != null && n !== 'NO';
}

/** Première page autorisée pour un membre de bureau (sans fiche membre). */
export function landingBureau(orgId: number, droits: MesDroitsDto | null): (string | number)[] {
  if (droits?.peutGestion) {
    for (const c of LANDING_CANDIDATES) {
      if (peut(droits, c.action)) {
        return ['/organisations', orgId, ...c.segments];
      }
    }
    return ['/organisations', orgId, 'mon-profil'];
  }
  return ['/organisations', orgId, 'mon-profil'];
}
