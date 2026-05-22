/** Modules du menu GIE et actions catalogue associées (au moins une ≠ NO = module visible). */
export type MenuModuleId =
  | 'membres'
  | 'comptes'
  | 'cotisations'
  | 'emprunts'
  | 'remboursements'
  | 'rapports'
  | 'exercices'
  | 'tresorerie'
  | 'parametrage'
  | 'utilisateurs'
  | 'notifications';

export interface MenuModuleDef {
  id: MenuModuleId;
  label: string;
  /** Une action autorisée suffit pour afficher le module. */
  actions: string[];
}

export const MENU_MODULES_GIE: MenuModuleDef[] = [
  { id: 'membres', label: 'Membres', actions: ['MEMBRE_LISTER', 'MEMBRE_GERER'] },
  { id: 'comptes', label: 'Comptes & Relevés', actions: ['SOLDE_ORG', 'SOLDE_AUTRES_MEMBRES'] },
  { id: 'cotisations', label: 'Cotisation', actions: ['OP_COTISATION'] },
  { id: 'emprunts', label: 'Emprunts', actions: ['OP_EMPRUNT'] },
  { id: 'remboursements', label: 'Remboursement', actions: ['OP_REMBOURSEMENT'] },
  { id: 'rapports', label: 'Rapports', actions: ['RAPPORT_COMPLET', 'RAPPORT_EXPORT'] },
  { id: 'exercices', label: 'Exercices', actions: ['PARAM_REGLES'] },
  {
    id: 'tresorerie',
    label: 'Trésorerie',
    actions: ['OP_DEPENSE', 'OP_BANQUE', 'OP_PENALITE'],
  },
  { id: 'parametrage', label: 'Paramétrage', actions: ['PARAM_REGLES', 'ADMIN_SECURITE'] },
  { id: 'utilisateurs', label: 'Utilisateurs & Droits', actions: ['ADMIN_UTILISATEURS'] },
  { id: 'notifications', label: 'Notifications', actions: ['ADMIN_JOURNAL'] },
];

export function moduleVisible(
  peutAction: (code: string) => boolean,
  module: MenuModuleDef
): boolean {
  if (module.id === 'notifications') {
    return module.actions.some(peutAction) || peutAction('MEMBRE_LISTER');
  }
  return module.actions.some(peutAction);
}
