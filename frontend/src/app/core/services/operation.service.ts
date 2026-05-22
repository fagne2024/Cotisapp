import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface MembreDto {
  id: number;
  codeMembre: string;
  nom: string;
  prenom: string;
  nomComplet: string;
  telephone?: string | null;
}

import type { ModePaiement } from '../../shared/util/mode-paiement.util';

export interface CotisationHebdoRequest {
  membreId: number;
  semaineKey: string;
  montant: number;
  dateOperation: string;
  observation?: string;
  /** Amende optionnelle (FCFA), bornée par la règle de cotisation. */
  montantAmende?: number | null;
  modePaiement?: ModePaiement;
  referencePaiement?: string;
}

export interface CotisationMoisRequest {
  membreId: number;
  moisAnnee: string;
  montant: number;
  dateOperation: string;
  observation?: string;
  montantAmende?: number | null;
  modePaiement?: ModePaiement;
  referencePaiement?: string;
}

export interface MouvementPreview {
  libelle: string;
  sens: string;
  montant: number;
}

@Injectable({ providedIn: 'root' })
export class OperationService {
  private readonly http = inject(HttpClient);

  listMembres(orgId: number) {
    return this.http.get<MembreDto[]>(`${environment.apiUrl}/organisations/${orgId}/membres`);
  }

  previewCotisationMois(orgId: number, body: CotisationMoisRequest) {
    return this.http.post<MouvementPreview[]>(
      `${environment.apiUrl}/organisations/${orgId}/operations/cotisation-mois/preview`,
      body
    );
  }

  validerCotisationMois(orgId: number, body: CotisationMoisRequest) {
    return this.http.post(
      `${environment.apiUrl}/organisations/${orgId}/operations/cotisation-mois`,
      body
    );
  }

  previewCotisationHebdo(orgId: number, body: CotisationHebdoRequest) {
    return this.http.post<MouvementPreview[]>(
      `${environment.apiUrl}/organisations/${orgId}/operations/cotisation-hebdo/preview`,
      body
    );
  }

  validerCotisationHebdo(orgId: number, body: CotisationHebdoRequest) {
    return this.http.post(
      `${environment.apiUrl}/organisations/${orgId}/operations/cotisation-hebdo`,
      body
    );
  }
}
