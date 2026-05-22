export type NotifTab = 'toutes' | 'nonlues' | 'urgences' | 'lues';
export type NotifSeverite = 'urgence' | 'warning' | 'info' | 'success';
export type NotifTypeFiltre = 'ALL' | 'URGENCE' | 'EMPRUNT' | 'COTISATION' | 'SYSTEME' | 'A_VALIDER';

export interface NotificationItem {
  id: string;
  groupe: string;
  severite: NotifSeverite;
  lu: boolean;
  icone: string;
  iconeClass: 'ico-re' | 'ico-or' | 'ico-g' | 'ico-bl' | 'ico-pu';
  titre: string;
  description: string;
  temps: string;
  tag: string;
  tagClass: 'tag-re' | 'tag-or' | 'tag-g' | 'tag-bl' | 'tag-pu' | 'tag-muted';
  actionLabel?: string;
  actionRoute?: (string | number)[];
  actionQueryParams?: Record<string, string>;
  typeFiltre: NotifTypeFiltre;
  demandeId?: number;
  workflowDemande?: boolean;
  /** Demande encore en attente (affiche Approuver / Rejeter). */
  demandeWorkflowActif?: boolean;
  /** COTISATION_HEBDO | COTISATION_MOIS | REMBOURSEMENT */
  demandeTypeDemande?: string;
  /** Amende saisie par le validateur à l'approbation (mobile money). */
  amendeApplicable?: boolean;
  montantAmendeMin?: number | null;
  montantAmendeMax?: number | null;
}

export interface NotifPreference {
  id: string;
  titre: string;
  sousTitre: string;
  actif: boolean;
}

export const PREFERENCES_DEMO: NotifPreference[] = [
  { id: 'email', titre: '📧 Emails', sousTitre: 'Alertes et résumés', actif: true },
  { id: 'push', titre: '📱 Push mobile', sousTitre: 'Notifications temps réel', actif: true },
  { id: 'rapport', titre: '📋 Rapport hebdo', sousTitre: 'Chaque lundi matin', actif: true },
  { id: 'retard', titre: '⚠ Emprunts retard', sousTitre: 'Alerte immédiate', actif: true },
  { id: 'cotisation', titre: '💰 Chaque cotisation', sousTitre: 'Peut être fréquent', actif: false },
];
