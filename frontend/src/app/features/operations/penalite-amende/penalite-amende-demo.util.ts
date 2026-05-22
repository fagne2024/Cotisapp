export type SanctionTypeUi = 'pen' | 'am';

export interface MotifOption {
  id: string;
  icon: string;
  label: string;
}

export interface HistoriqueSanction {
  membreNom: string;
  codeMembre: string;
  type: SanctionTypeUi;
  motif: string;
  montant: number;
  dateLabel: string;
}

export interface TopPenalise {
  nom: string;
  codeMembre: string;
  initials: string;
  avColor: string;
  detail: string;
  total: number;
  highlight: boolean;
}

export const MOTIFS_PENALITE: MotifOption[] = [
  { id: 'absence', icon: '🚶', label: 'Absence réunion' },
  { id: 'retard', icon: '⏰', label: 'Retard cotisation' },
  { id: 'reglement', icon: '📵', label: 'Non-respect règlement' },
  { id: 'obligations', icon: '🤐', label: 'Manquement aux obligations' },
  { id: 'comportement', icon: '💬', label: 'Comportement inapproprié' },
  { id: 'autre', icon: '📝', label: 'Autre motif' },
];

export const MOTIFS_AMENDE: MotifOption[] = [
  { id: 'reglement', icon: '📵', label: 'Non-respect règlement' },
  { id: 'absence', icon: '🚶', label: 'Absence réunion' },
  { id: 'comportement', icon: '💬', label: 'Comportement inapproprié' },
  { id: 'retard', icon: '⏰', label: 'Retard cotisation' },
  { id: 'obligations', icon: '🤐', label: 'Manquement aux obligations' },
  { id: 'autre', icon: '📝', label: 'Autre motif' },
];

