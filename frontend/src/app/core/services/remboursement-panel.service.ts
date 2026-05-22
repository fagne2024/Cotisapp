import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { OperationAnnulationDto } from './emprunt.service';

export interface RemboursementRecentDto {
  operationId: number;
  membreNom: string;
  typeEmprunt: 'ETALE' | 'CAISSE' | 'SOLIDARITE';
  typeLibelle: string;
  montantTotal: number;
  dateLabel: string;
  meta: string;
  iconeClass: string;
}

export interface RemboursementPanneauDto {
  soldeCaisse: number;
  soldeSolidarite: number;
  recentes: RemboursementRecentDto[];
}

export interface RemboursementHistoriqueLigneDto {
  ligneId: string;
  operationId: number;
  empruntId: number | null;
  typeEmprunt: 'ETALE' | 'CAISSE' | 'SOLIDARITE';
  typeLibelle: string;
  membreId: number | null;
  membreNom: string;
  codeMembre: string;
  montantCapital: number;
  montantFrais: number;
  montantPenalite: number;
  montantTotal: number;
  dateOperation: string;
  dateLabel: string;
  observation?: string | null;
  modePaiementLibelle?: string | null;
  referencePaiement?: string | null;
  annulee: boolean;
  annulable: boolean;
}

@Injectable({ providedIn: 'root' })
export class RemboursementPanelService {
  private readonly http = inject(HttpClient);

  chargerPanneau(orgId: number) {
    return this.http.get<RemboursementPanneauDto>(
      `${environment.apiUrl}/organisations/${orgId}/remboursements/panneau`
    );
  }

  listerHistorique(orgId: number) {
    return this.http.get<RemboursementHistoriqueLigneDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/remboursements/historique`
    );
  }

  annulerOperation(orgId: number, operationId: number) {
    return this.http.post<OperationAnnulationDto>(
      `${environment.apiUrl}/organisations/${orgId}/remboursements/operations/${operationId}/annuler`,
      {}
    );
  }
}
