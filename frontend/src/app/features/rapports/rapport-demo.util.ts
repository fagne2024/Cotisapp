export type RapportTabUi = 'cotis' | 'empr' | 'membres' | 'depenses';

export interface HeroStat {
  valeur: string;
  label: string;
  trend: string;
}

export interface BarChartItem {
  label: string;
  valeurLabel: string;
  heightPct: number;
  belowTarget?: boolean;
}

export interface CotisationMembreRow {
  nom: string;
  code: string;
  initials: string;
  avColor: string;
  posteLabel: string;
  posteBadgeClass: string;
  hebdo: string;
  mois: string;
  solidarite: string;
  total: string;
  statut: 'complet' | 'manque' | 'partiel';
  statutLabel: string;
}

export interface EmpruntRapportCard {
  nom: string;
  badge: string;
  badgeClass: string;
  detail: string;
  rembourse: string;
  total: string;
  pct: number;
  barClass: 'red' | 'blue' | 'green';
  borderClass: 'retard' | 'sol' | 'cours';
  bgClass?: string;
}

export interface MembreFinancierRow {
  nom: string;
  code: string;
  initials: string;
  avColor: string;
  posteHtml: string;
  epargne: string;
  solidarite: string;
  penalite: string;
  amende: string;
  emprunt: string;
  situation: string;
  situationClass: string;
}

export interface DepenseRapportRow {
  categorie: string;
  beneficiaire: string;
  description: string;
  montant: number;
  dateLabel: string;
  saisiPar: string;
}

export const HERO_STATS_DEMO: HeroStat[] = [
  { valeur: '528 000 F', label: 'Cotisations collectées', trend: '↑ +8% vs avril' },
  { valeur: '245 000 F', label: 'Emprunts actifs', trend: '8 emprunt(s) · ⚠ 2 en retard' },
  { valeur: '1 842 500 F', label: 'Solde Caisse', trend: '↑ +12%' },
  { valeur: '324 000 F', label: 'Fonds Solidarité', trend: '↑ +5%' },
  { valeur: '16 500 F', label: 'Pénalités / Amendes', trend: '7 pénalités · 2 amendes' },
];

export const CHART_BARS_DEMO: BarChartItem[] = [
  { label: 'S18', valeurLabel: '105k', heightPct: 78 },
  { label: 'S19', valeurLabel: '112k', heightPct: 83 },
  { label: 'S20', valeurLabel: '95k', heightPct: 70, belowTarget: true },
  { label: 'S21', valeurLabel: '122k', heightPct: 90 },
  { label: 'S22', valeurLabel: '135k', heightPct: 100 },
];

export const COTISATIONS_MEMBRES_DEMO: CotisationMembreRow[] = [
  {
    nom: 'Fatou Diallo',
    code: 'GDR-003',
    initials: 'FD',
    avColor: '#7c3aed',
    posteLabel: '👑 Présidente',
    posteBadgeClass: 'b-pu',
    hebdo: '4 × 5 000 F',
    mois: '8 000 F',
    solidarite: '5 × 200 F',
    total: '29 000 F',
    statut: 'complet',
    statutLabel: '✓ Complet',
  },
  {
    nom: 'Fatou Bâ',
    code: 'GDR-001',
    initials: 'FB',
    avColor: 'var(--g2)',
    posteLabel: '👤 Simple',
    posteBadgeClass: 'b-gray',
    hebdo: '4 × 5 000 F',
    mois: '—',
    solidarite: '4 × 200 F',
    total: '20 800 F',
    statut: 'complet',
    statutLabel: '✓ Complet',
  },
  {
    nom: 'Mamadou Sow',
    code: 'GDR-002',
    initials: 'MS',
    avColor: 'var(--re)',
    posteLabel: '👤 Simple',
    posteBadgeClass: 'b-gray',
    hebdo: '3 × 5 000 F',
    mois: '—',
    solidarite: '3 × 200 F',
    total: '15 600 F',
    statut: 'manque',
    statutLabel: '⚠ Manque 1',
  },
  {
    nom: 'Mariama Bah',
    code: 'GDR-004',
    initials: 'MB',
    avColor: 'var(--g1)',
    posteLabel: '💼 Trésorière',
    posteBadgeClass: 'b-green',
    hebdo: '4 × 5 000 F',
    mois: '10 000 F',
    solidarite: '5 × 200 F',
    total: '31 000 F',
    statut: 'complet',
    statutLabel: '✓ Complet',
  },
  {
    nom: 'Oumar Diallo',
    code: 'GDR-007',
    initials: 'OD',
    avColor: 'var(--bl)',
    posteLabel: '📝 S.G.',
    posteBadgeClass: 'b-blue',
    hebdo: '4 × 3 000 F',
    mois: '6 000 F',
    solidarite: '5 × 200 F',
    total: '19 000 F',
    statut: 'complet',
    statutLabel: '✓ Complet',
  },
];

export const EMPRUNTS_CARDS_DEMO: EmpruntRapportCard[] = [
  {
    nom: 'Mamadou Sow',
    badge: '⚠ Retard',
    badgeClass: 'b-red',
    detail: 'Étalé · Éch. 2/3 en retard · 3 mois restants',
    rembourse: '50 000',
    total: '115 000 F',
    pct: 43,
    barClass: 'red',
    borderClass: 'retard',
    bgClass: 're2',
  },
  {
    nom: 'Aïda Ndiaye',
    badge: 'Solidarité',
    badgeClass: 'b-blue',
    detail: 'Sans frais · 1 échéance restante',
    rembourse: '20 000',
    total: '30 000 F',
    pct: 67,
    barClass: 'blue',
    borderClass: 'sol',
  },
  {
    nom: 'Oumar Diallo (S.G.)',
    badge: 'Caisse',
    badgeClass: 'b-or',
    detail: 'Avec frais membre · En cours',
    rembourse: '30 000',
    total: '54 000 F',
    pct: 56,
    barClass: 'green',
    borderClass: 'cours',
  },
];

export const MEMBRES_FINANCIER_DEMO: MembreFinancierRow[] = [
  {
    nom: 'Fatou Diallo',
    code: 'GDR-003',
    initials: 'FD',
    avColor: '#7c3aed',
    posteHtml: 'b-pu',
    epargne: '62 000 F',
    solidarite: '8 400 F',
    penalite: '0 F',
    amende: '0 F',
    emprunt: '—',
    situation: '✓ Excellent',
    situationClass: 'b-green',
  },
  {
    nom: 'Fatou Bâ',
    code: 'GDR-001',
    initials: 'FB',
    avColor: 'var(--g2)',
    posteHtml: 'b-gray',
    epargne: '45 000 F',
    solidarite: '6 400 F',
    penalite: '0 F',
    amende: '0 F',
    emprunt: '—',
    situation: '✓ Bon',
    situationClass: 'b-green',
  },
  {
    nom: 'Mamadou Sow',
    code: 'GDR-002',
    initials: 'MS',
    avColor: 'var(--re)',
    posteHtml: 'b-gray',
    epargne: '38 000 F',
    solidarite: '5 200 F',
    penalite: '6 000 F',
    amende: '0 F',
    emprunt: '65 000 F ⚠',
    situation: '⚠ Retard',
    situationClass: 'b-red',
  },
  {
    nom: 'Mariama Bah',
    code: 'GDR-004',
    initials: 'MB',
    avColor: 'var(--g1)',
    posteHtml: 'b-green',
    epargne: '55 000 F',
    solidarite: '7 600 F',
    penalite: '0 F',
    amende: '0 F',
    emprunt: '—',
    situation: '✓ Excellent',
    situationClass: 'b-green',
  },
];

export const DEPENSES_RAPPORT_DEMO: DepenseRapportRow[] = [
  {
    categorie: '🍽 Restauration',
    beneficiaire: 'Chez Mame Diarra',
    description: 'Réunion mensuelle bureau',
    montant: 25000,
    dateLabel: '05/05',
    saisiPar: 'A. Diallo',
  },
  {
    categorie: '📦 Fournitures',
    beneficiaire: 'Librairie Sankharé',
    description: 'Cahiers de présence',
    montant: 10000,
    dateLabel: '03/05',
    saisiPar: 'A. Diallo',
  },
  {
    categorie: '🚗 Transport',
    beneficiaire: 'Garage Thiès',
    description: 'Déplacement terrain',
    montant: 8500,
    dateLabel: '01/05',
    saisiPar: 'A. Diallo',
  },
  {
    categorie: '📞 Communication',
    beneficiaire: 'Orange Sénégal',
    description: 'Recharge mobile bureau',
    montant: 5000,
    dateLabel: '28/04',
    saisiPar: 'A. Diallo',
  },
];

export const PERIODES = [
  { value: '2026-05', label: 'Mai 2026' },
  { value: '2026-04', label: 'Avril 2026' },
  { value: '2026-03', label: 'Mars 2026' },
  { value: '2026', label: 'Année 2026' },
];
