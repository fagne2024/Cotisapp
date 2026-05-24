import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface CompteOrgCardDto {
  compteId: number;
  typeCompte: string;
  libelle: string;
  sousTitre: string;
  solde: number;
  variationJour: number;
  icone: string;
}

export interface CompteMembreResumeDto {
  membreId: number;
  nomComplet: string;
  codeMembre: string;
  posteLabel: string;
  initials: string;
  avatarColor: string;
  totalSoldes: number;
  epargne: number;
  solidarite: number;
  depense: number;
  penalite: number;
  amende: number;
}

export interface CompteReleveSyntheseDto {
  comptesOrganisation: CompteOrgCardDto[];
  totalActifs: number;
  encoursEmprunts: number;
  nbEmpruntsEnCours: number;
  variationJourGlobale: number;
  membres: CompteMembreResumeDto[];
}

export interface ReleveLigneDto {
  operationId: number;
  dateOperation: string;
  heureOperation?: string | null;
  titre: string;
  typeOperation: string;
  typeLibelle: string;
  typeTagClass: string;
  sens: 'credit' | 'debit';
  montant: number;
  soldeApres: number;
  annulee: boolean;
  contrepassation: boolean;
  reference: string;
  membreNom?: string | null;
  codeMembre?: string | null;
  icone: string;
  iconeBg: string;
  metaExtra?: string | null;
}

export interface ReleveGroupeDto {
  label: string;
  date: string;
  lignes: ReleveLigneDto[];
}

export interface ReleveTotauxDto {
  entrees: number;
  sorties: number;
  variationNette: number;
  nbOperations: number;
  nbAnnulees: number;
}

export interface CompteReleveDto {
  scope: string;
  compteId?: number | null;
  membreId?: number | null;
  titre: string;
  meta: string;
  icone: string;
  iconeBg: string;
  soldeActuel: number;
  soldeSolidarite?: number | null;
  soldeDepense?: number | null;
  soldePenalitesAmendes?: number | null;
  variationJour: number;
  entreesMois: number;
  sortiesMois: number;
  variationMois: number;
  dateDebut: string;
  dateFin: string;
  groupes: ReleveGroupeDto[];
  totaux: ReleveTotauxDto;
}

export interface ReleveQuery {
  scope: string;
  compteId?: number;
  membreId?: number;
  dateDebut?: string;
  dateFin?: string;
  type?: string;
  statut?: string;
  q?: string;
}

@Injectable({ providedIn: 'root' })
export class CompteReleveService {
  private readonly http = inject(HttpClient);

  chargerSynthese(orgId: number) {
    return this.http.get<CompteReleveSyntheseDto>(
      `${environment.apiUrl}/organisations/${orgId}/comptes-releves/synthese`
    );
  }

  chargerReleve(orgId: number, query: ReleveQuery) {
    let params = new HttpParams().set('scope', query.scope);
    if (query.compteId != null) params = params.set('compteId', query.compteId);
    if (query.membreId != null) params = params.set('membreId', query.membreId);
    if (query.dateDebut) params = params.set('dateDebut', query.dateDebut);
    if (query.dateFin) params = params.set('dateFin', query.dateFin);
    if (query.type) params = params.set('type', query.type);
    if (query.statut) params = params.set('statut', query.statut);
    if (query.q) params = params.set('q', query.q);
    return this.http.get<CompteReleveDto>(
      `${environment.apiUrl}/organisations/${orgId}/comptes-releves/releve`,
      { params }
    );
  }
}
