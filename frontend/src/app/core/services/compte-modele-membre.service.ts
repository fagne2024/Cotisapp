import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface CompteModeleMembreDto {
  id: number;
  code: string;
  libelle: string;
  actif: boolean;
}

export interface CreateCompteModeleMembreBody {
  code: string;
  libelle: string;
}

@Injectable({ providedIn: 'root' })
export class CompteModeleMembreService {
  private readonly http = inject(HttpClient);

  lister(orgId: number, actifsSeulement = true) {
    return this.http.get<CompteModeleMembreDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/comptes-modeles-membre`,
      { params: actifsSeulement ? { actifsSeulement: 'true' } : {} }
    );
  }

  creer(orgId: number, body: CreateCompteModeleMembreBody) {
    return this.http.post<CompteModeleMembreDto>(
      `${environment.apiUrl}/organisations/${orgId}/comptes-modeles-membre`,
      body
    );
  }
}
