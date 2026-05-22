import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface RapportPeriodeOption {
  value: string;
  label: string;
}

export interface RapportHeroStat {
  valeur: string;
  label: string;
  trend: string;
}

export interface RapportBarChartItem {
  label: string;
  valeurLabel: string;
  heightPct: number;
  belowTarget?: boolean;
}

export interface RapportParticipation {
  pctGlobal: number;
  membresAJour: number;
  membresTotal: number;
  hebdoPayes: number;
  hebdoTotal: number;
  moisPayes: number;
  moisTotal: number;
  bureauPayes: number;
  bureauTotal: number;
}

export interface RapportCotisationMembre {
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
  statut: 'complet' | 'partiel' | 'manque';
  statutLabel: string;
  totalMontant?: number;
}

export interface RapportEmpruntCard {
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

export interface RapportEmpruntSynthese {
  enCours: number;
  enRetard: number;
  soldesMois: number;
  encoursTotal: number;
  remboursementsMois: number;
  fraisRestants: number;
}

export interface RapportMembreFinancier {
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

export interface RapportDepense {
  categorie: string;
  categorieId?: string;
  beneficiaire: string;
  description: string;
  montant: number;
  dateLabel: string;
  saisiPar: string;
}

export interface RapportDto {
  periode: string;
  periodeLabel: string;
  nbMembresActifs: number;
  nbMembresBureau: number;
  periodesDisponibles: RapportPeriodeOption[];
  heroStats: RapportHeroStat[];
  cotisationsParSemaine: RapportBarChartItem[];
  participation: RapportParticipation;
  totalCotisations: number;
  cotisationsMembres: RapportCotisationMembre[];
  emprunts: RapportEmpruntCard[];
  empruntsSynthese: RapportEmpruntSynthese;
  membresFinancier: RapportMembreFinancier[];
  depenses: RapportDepense[];
  totalDepenses: number;
}

export interface RapportMembreDto {
  membreId: number;
  nom: string;
  code: string;
  initials: string;
  avColor: string;
  posteLabel: string;
  posteBadgeClass: string;
  periode: string;
  periodeLabel: string;
  periodesDisponibles: RapportPeriodeOption[];
  heroStats: RapportHeroStat[];
  hebdo: string;
  mois: string;
  solidarite: string;
  totalCotisationsLabel: string;
  totalCotisations: number;
  statutCotisation: 'complet' | 'partiel' | 'manque';
  statutCotisationLabel: string;
  cotisationsParSemaine: RapportBarChartItem[];
  emprunts: RapportEmpruntCard[];
  operations: RapportMembreOperationLigne[];
  soldeMembre: {
    solde: number;
    epargne: number;
    solidarite: number;
    emprunts: number;
    fraisEmprunt: number;
    remboursements: number;
    fraisRemboursement: number;
  };
  comptes: { typeCompte: string; libelle: string; solde: number; soldeLabel: string }[];
}

export interface RapportMembreOperationLigne {
  id: number;
  dateOperation: string;
  dateLabel: string;
  typeOperation: string;
  libelle: string;
  montant: number;
  montantLabel: string;
  sens: 'credit' | 'debit';
}

@Injectable({ providedIn: 'root' })
export class RapportService {
  private readonly http = inject(HttpClient);

  chargerMembre(orgId: number, membreId: number, periode?: string) {
    let params = new HttpParams();
    if (periode) {
      params = params.set('periode', periode);
    }
    return this.http.get<RapportMembreDto>(
      `${environment.apiUrl}/organisations/${orgId}/membres/${membreId}/rapport`,
      { params }
    );
  }

  charger(orgId: number, periode?: string) {
    let params = new HttpParams();
    if (periode) {
      params = params.set('periode', periode);
    }
    return this.http.get<RapportDto>(`${environment.apiUrl}/organisations/${orgId}/rapports`, {
      params,
    });
  }
}
