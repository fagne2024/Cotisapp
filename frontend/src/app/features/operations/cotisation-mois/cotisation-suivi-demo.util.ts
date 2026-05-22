export type SuiviStatutUi = 'paye' | 'attente';

export interface SuiviCotisationRow {
  membreId: number;
  initials: string;
  avColor: string;
  nom: string;
  sousTitre: string;
  statut: SuiviStatutUi;
}