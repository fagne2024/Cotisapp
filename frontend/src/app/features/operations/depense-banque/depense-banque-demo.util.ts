export type DepenseBanquePageUi = 'dep' | 'bk' | 'cais';

export interface MouvementCaisse {
  id: number;
  dateLabel: string;
  sens: 'credit' | 'debit';
  montant: number;
  soldeApres: number;
  typeOperation: string;
  libelle: string;
}
export type BanqueOpUi = 'vers' | 'ret';

export interface CategorieDepense {
  id: string;
  icon: string;
  label: string;
}

export interface DepenseRecente {
  categorieId: string;
  categorie: string;
  montant: number;
  dateLabel: string;
  beneficiaire?: string;
}

export interface DepenseParCategorie {
  icon: string;
  label: string;
  montant: number;
}

export interface MouvementBancaire {
  dateLabel: string;
  type: 'vers' | 'ret';
  montant: number;
  soldeApres: number;
  releveId?: number;
  releveNomFichier?: string;
}

export const CATEGORIES_DEPENSE: CategorieDepense[] = [
  { id: 'restauration', icon: '🍽', label: 'Restauration' },
  { id: 'transport', icon: '🚗', label: 'Transport' },
  { id: 'fournitures', icon: '📦', label: 'Fournitures' },
  { id: 'loyer', icon: '🏠', label: 'Loyer local' },
  { id: 'energie', icon: '💡', label: 'Électricité / Eau' },
  { id: 'communication', icon: '📞', label: 'Communication' },
  { id: 'sante', icon: '🏥', label: 'Santé / Urgence' },
  { id: 'autre', icon: '📝', label: 'Autre' },
];

export const DEPENSES_RECENTES_DEMO: DepenseRecente[] = [
  { categorieId: 'transport', categorie: '🚗 Transport', montant: 8500, dateLabel: '03/05' },
  { categorieId: 'fournitures', categorie: '📦 Fournitures', montant: 10000, dateLabel: '01/05' },
  {
    categorieId: 'communication',
    categorie: '📞 Comm.',
    montant: 5000,
    dateLabel: '28/04',
  },
];

export const DEPENSES_PAR_CAT_DEMO: DepenseParCategorie[] = [
  { icon: '🍽', label: 'Restauration', montant: 25000 },
  { icon: '🚗', label: 'Transport', montant: 8500 },
  { icon: '📦', label: 'Fournitures', montant: 10000 },
  { icon: '📞', label: 'Communication', montant: 5000 },
];

export const MOUVEMENTS_BANQUE_DEMO: MouvementBancaire[] = [
  { dateLabel: '07/05', type: 'vers', montant: 150000, soldeApres: 650000 },
  { dateLabel: '28/04', type: 'ret', montant: 50000, soldeApres: 500000 },
  { dateLabel: '14/04', type: 'vers', montant: 200000, soldeApres: 550000 },
  { dateLabel: '01/04', type: 'vers', montant: 100000, soldeApres: 350000 },
];

export const SOLDE_CAISSE_DEMO = 1_842_500;
export const SOLDE_BANQUE_DEMO = 650_000;
export const TOTAL_DEPENSES_MOIS_DEMO = 48_500;
