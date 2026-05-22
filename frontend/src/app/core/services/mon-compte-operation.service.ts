import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import type {
  CotisationHebdoRequest,
  CotisationMoisRequest,
  MouvementPreview,
} from './operation.service';
import type { RembourserRequest } from './emprunt.service';

export interface DemandeOperationMembreResponse {
  id: number;
  membreId: number;
  membreNom: string;
  codeMembre: string;
  typeDemande: 'COTISATION_HEBDO' | 'COTISATION_MOIS' | 'REMBOURSEMENT';
  statut: 'EN_ATTENTE' | 'VALIDEE' | 'REFUSEE';
  montant: number;
  modePaiement?: string | null;
  referencePaiement?: string | null;
  libelleResume?: string | null;
  dateDemande: string;
  dateTraitement?: string | null;
  motifRefus?: string | null;
  message?: string | null;
}

@Injectable({ providedIn: 'root' })
export class MonCompteOperationService {
  private readonly http = inject(HttpClient);
  private readonly base = (orgId: number) =>
    `${environment.apiUrl}/organisations/${orgId}/mon-compte`;

  previewCotisationHebdo(orgId: number, body: CotisationHebdoRequest) {
    return this.http.post<MouvementPreview[]>(
      `${this.base(orgId)}/operations/cotisation-hebdo/preview`,
      body
    );
  }

  mesDemandesEnAttente(orgId: number) {
    return this.http.get<DemandeOperationMembreResponse[]>(`${this.base(orgId)}/demandes-en-attente`);
  }

  mesDemandes(orgId: number) {
    return this.http.get<DemandeOperationMembreResponse[]>(`${this.base(orgId)}/mes-demandes`);
  }

  validerCotisationHebdo(orgId: number, body: CotisationHebdoRequest) {
    return this.http.post<DemandeOperationMembreResponse>(`${this.base(orgId)}/operations/cotisation-hebdo`, body);
  }

  previewCotisationMois(orgId: number, body: CotisationMoisRequest) {
    return this.http.post<MouvementPreview[]>(
      `${this.base(orgId)}/operations/cotisation-mois/preview`,
      body
    );
  }

  validerCotisationMois(orgId: number, body: CotisationMoisRequest) {
    return this.http.post<DemandeOperationMembreResponse>(`${this.base(orgId)}/operations/cotisation-mois`, body);
  }

  rembourser(orgId: number, empruntId: number, body: RembourserRequest) {
    return this.http.post<DemandeOperationMembreResponse>(
      `${this.base(orgId)}/emprunts/${empruntId}/rembourser`,
      body
    );
  }
}
