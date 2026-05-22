import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { MembreDto, OperationMembreDto } from './membre.service';

export interface CotisationMoisStatDto {
  mois: number;
  montantCotisations: number;
  objectif: number;
}

export interface DashboardDto {
  soldeCaisse: number;
  soldeSolidarite: number;
  soldeBanque: number;
  nbMembresActifs: number;
  nbMembresBureau: number;
  nbMembresSimples: number;
  nbEmpruntsEnCours: number;
  nbEmpruntsEnRetard: number;
  operationsRecentes: OperationMembreDto[];
  bureau: MembreDto[];
  evolutionCotisations?: CotisationMoisStatDto[];
  evolutionAnnee?: number;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  obtenir(orgId: number) {
    return this.http.get<DashboardDto>(`${environment.apiUrl}/organisations/${orgId}/dashboard`);
  }
}
