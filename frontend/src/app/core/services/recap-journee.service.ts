import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import type { StatutPlanad } from './exercice.service';

export interface JourneeReunionDto {
  id: number;
  numero: number;
  dateReunion: string;
  libelle: string;
  statut: StatutPlanad;
  dateCloture?: string | null;
  nbOperations: number;
  nbCotisations: number;
  nbEmprunts: number;
  nbRemboursements: number;
}

export interface RecapJourneeSyntheseDto {
  nbOperationsActives: number;
  nbAnnulations: number;
  nbCotisations: number;
  montantCotisations: number;
  nbEmprunts: number;
  montantEmprunts: number;
  nbRemboursements: number;
  montantRemboursements: number;
  nbMembresConcernes: number;
  entreesCaisse: number;
  sortiesCaisse: number;
}

export interface RecapCompteDto {
  typeCompte: string;
  libelle: string;
  variationJour: number;
  soldeFinJournee: number;
  soldeActuel: number;
}

export interface RecapMembreDto {
  membreId: number;
  codeMembre: string;
  membreNom: string;
  nbOperations: number;
  montantCotisations: number;
  montantEmprunts: number;
  montantRemboursements: number;
  variationNetComptes: number;
}

export interface RecapOperationLigneDto {
  operationId: number;
  typeOperation: string;
  typeLibelle: string;
  membreId: number | null;
  membreNom: string | null;
  codeMembre: string | null;
  montant: number;
  montantFrais: number;
  montantTotal: number;
  dateOperation: string;
  observation: string | null;
  annulee: boolean;
  annulation: boolean;
}

export interface RecapJourneeDto {
  journeeId: number;
  codeOrganisation: string;
  libelle: string;
  numero: number;
  dateReunion: string;
  synthese: RecapJourneeSyntheseDto;
  comptesOrganisation: RecapCompteDto[];
  membres: RecapMembreDto[];
  operations: RecapOperationLigneDto[];
}

@Injectable({ providedIn: 'root' })
export class RecapJourneeService {
  private readonly http = inject(HttpClient);

  lister(orgId: number, exerciceId?: number) {
    const params: Record<string, string> = {};
    if (exerciceId != null) {
      params['exerciceId'] = String(exerciceId);
    }
    return this.http.get<JourneeReunionDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/recap-journees`,
      { params }
    );
  }

  creer(orgId: number, dateReunion: string) {
    return this.http.post<JourneeReunionDto>(
      `${environment.apiUrl}/organisations/${orgId}/recap-journees`,
      { dateReunion }
    );
  }

  cloturer(orgId: number, journeeId: number) {
    return this.http.post<JourneeReunionDto>(
      `${environment.apiUrl}/organisations/${orgId}/recap-journees/${journeeId}/cloturer`,
      {}
    );
  }

  reouvrir(orgId: number, journeeId: number) {
    return this.http.post<JourneeReunionDto>(
      `${environment.apiUrl}/organisations/${orgId}/recap-journees/${journeeId}/reouvrir`,
      {}
    );
  }

  obtenirRecap(orgId: number, journeeId: number) {
    return this.http.get<RecapJourneeDto>(
      `${environment.apiUrl}/organisations/${orgId}/recap-journees/${journeeId}`
    );
  }

  obtenirRecapParDate(orgId: number, date: string) {
    return this.http.get<RecapJourneeDto>(
      `${environment.apiUrl}/organisations/${orgId}/recap-journees/par-date`,
      { params: { date } }
    );
  }
}
