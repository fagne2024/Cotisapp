import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

export type SanctionTypeApi = 'pen' | 'am';

export interface PenaliteAmendeHistoriqueLigneDto {
  operationId: number;
  membreId: number | null;
  membreNom: string;
  codeMembre: string;
  type: SanctionTypeApi;
  motif: string;
  montant: number;
  dateOperation: string;
  dateLabel: string;
  annulee: boolean;
}

export interface PenaliteAmendeStatsMoisDto {
  moisLabel: string;
  penalites: number;
  amendes: number;
  totalEncaisse: number;
}

export interface PenaliteAmendeTopMembreDto {
  membreId: number;
  nom: string;
  codeMembre: string;
  detail: string;
  total: number;
}

export interface PenaliteAmendePanneauDto {
  soldeCaisse: number;
  statsMois: PenaliteAmendeStatsMoisDto;
  historique: PenaliteAmendeHistoriqueLigneDto[];
  topPenalises: PenaliteAmendeTopMembreDto[];
}

export interface AppliquerSanctionRequest {
  membreId: number;
  type: 'PENALITE' | 'AMENDE';
  montant: number;
  dateOperation: string;
  motif: string;
  observation?: string | null;
}

@Injectable({ providedIn: 'root' })
export class PenaliteAmendeService {
  private readonly http = inject(HttpClient);

  chargerPanneau(orgId: number) {
    return this.http.get<PenaliteAmendePanneauDto>(
      `${environment.apiUrl}/organisations/${orgId}/penalites-amendes/panneau`
    );
  }

  appliquer(orgId: number, body: AppliquerSanctionRequest) {
    return this.http.post(
      `${environment.apiUrl}/organisations/${orgId}/penalites-amendes/appliquer`,
      body
    );
  }
}
