import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export type TypeEmprunt = 'ETALE' | 'SOLIDARITE' | 'CAISSE';

export interface EcheanceDto {
  id: number;
  numero: number;
  montantEcheance: number;
  montantPaye: number;
  dateEcheance: string;
  statut: string;
}

export interface EmpruntDto {
  id: number;
  membreId: number;
  membreNom: string;
  codeMembre: string;
  typeEmprunt: TypeEmprunt;
  montantTotal: number;
  montantRembourse: number;
  montantRestant: number;
  montantFrais?: number;
  /** Avance Caisse à l'octroi (Solidarité, solde insuffisant). */
  montantAvanceCaisse?: number;
  montantRembourseAvanceCaisse?: number;
  montantAvanceCaisseRestant?: number;
  statut: string;
  dateCreation?: string;
  echeances: EcheanceDto[];
}

export interface AccorderEmpruntBody {
  membreId: number;
  typeEmprunt: TypeEmprunt;
  montant: number;
  nbEcheances?: number;
  dateOctroi: string;
  observation?: string;
}

export interface OperationAnnulationDto {
  operationOrigineId: number;
  operationAnnulationId: number;
  message: string;
  dateAnnulation: string;
  mouvementsInverses: number;
}

export interface EmpruntHistoriqueLigneDto {
  ligneId: string;
  operationId: number;
  empruntId: number | null;
  typeEmprunt: TypeEmprunt;
  typeLibelle: string;
  membreId: number | null;
  membreNom: string;
  codeMembre: string;
  montantCapital: number;
  montantFrais: number;
  montantTotal: number;
  nbEcheances: number | null;
  dateOperation: string;
  dateLabel: string;
  observation?: string | null;
  annulee: boolean;
  annulable: boolean;
}

export interface RembourserRequest {
  echeanceId?: number;
  montant?: number;
  montantCapital?: number;
  montantFrais?: number;
  montantPenalite?: number;
  appliquerPenalite?: boolean;
  datePaiement: string;
  modePaiement?: string;
  referencePaiement?: string;
  observation?: string;
}

@Injectable({ providedIn: 'root' })
export class EmpruntService {
  private readonly http = inject(HttpClient);

  /** Emprunts EN_COURS (octroi, remboursements, panneau latéral). */
  lister(orgId: number, type?: TypeEmprunt) {
    return this.http.get<EmpruntDto[]>(`${environment.apiUrl}/organisations/${orgId}/emprunts`, type ? { params: { type } } : {});
  }

  /** Suivi : emprunts en cours et soldés. */
  listerSuivi(orgId: number, type?: TypeEmprunt) {
    return this.http.get<EmpruntDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/emprunts/suivi`,
      type ? { params: { type } } : {}
    );
  }

  /** Emprunts du membre connecté (cloisonné). */
  listerMesEmprunts(orgId: number, type?: TypeEmprunt) {
    return this.http.get<EmpruntDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/mon-compte/emprunts`,
      type ? { params: { type } } : {}
    );
  }

  /** Suivi membre : en cours et soldés. */
  listerMesEmpruntsSuivi(orgId: number, type?: TypeEmprunt) {
    return this.http.get<EmpruntDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/mon-compte/emprunts/suivi`,
      type ? { params: { type } } : {}
    );
  }

  obtenir(orgId: number, empruntId: number) {
    return this.http.get<EmpruntDto>(`${environment.apiUrl}/organisations/${orgId}/emprunts/${empruntId}`);
  }

  accorder(orgId: number, body: AccorderEmpruntBody) {
    return this.http.post<EmpruntDto>(`${environment.apiUrl}/organisations/${orgId}/emprunts`, body);
  }

  listerHistorique(orgId: number) {
    return this.http.get<EmpruntHistoriqueLigneDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/emprunts/historique`
    );
  }

  annulerOctroi(orgId: number, operationId: number) {
    return this.http.post<OperationAnnulationDto>(
      `${environment.apiUrl}/organisations/${orgId}/emprunts/operations/${operationId}/annuler`,
      {}
    );
  }

  rembourser(orgId: number, empId: number, type: TypeEmprunt, body: RembourserRequest) {
    const path =
      type === 'ETALE' ? 'etale' : type === 'SOLIDARITE' ? 'solidarite' : 'caisse';
    return this.http.post(
      `${environment.apiUrl}/organisations/${orgId}/emprunts/${empId}/rembourser/${path}`,
      body
    );
  }
}
