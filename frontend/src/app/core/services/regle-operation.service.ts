import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export type TypeOperation =
  | 'COTISATION'
  | 'COTISATION_MOIS'
  | 'VERSEMENT'
  | 'EMPRUNT'
  | 'REMBOURSEMENT'
  | 'PENALITE'
  | 'AMENDE'
  | 'DEPENSE'
  | 'BANQUE_VERSEMENT'
  | 'BANQUE_RETRAIT';

export type Periodicite = 'HEBDOMADAIRE' | 'MENSUEL' | 'LIBRE';

export type TypeModeCalcul = 'FIXE' | 'POURCENTAGE';

export type UniteEcheance = 'MOIS' | 'JOURS';

export type SensMouvement = 'DEBIT' | 'CREDIT';

export interface MouvementRegleDto {
  id?: number;
  ordre: number;
  sourceType: string;
  cibleType: string;
  sens: SensMouvement;
  typeMontant: string;
}

export interface RegleOperationDto {
  id: number;
  typeOperation: TypeOperation;
  libelle: string;
  periodicite: Periodicite | null;
  montantMin: number | null;
  montantMax: number | null;
  montantParPart?: number | null;
  partsMin?: number | null;
  partsMax?: number | null;
  solidariteAuto: boolean;
  montantSolidariteAuto: number | null;
  montantAmendeMin?: number | null;
  montantAmendeMax?: number | null;
  typeFrais?: TypeModeCalcul | null;
  montantFrais?: number | null;
  pourcentageFrais?: number | null;
  nbEcheancesMin?: number | null;
  nbEcheancesMax?: number | null;
  nbEcheancesDefaut?: number | null;
  uniteEcheance?: UniteEcheance | null;
  jourEcheanceMois?: number | null;
  /** Jours avant l'échéance pour l'alerte « proche » (emprunts). */
  joursAlerteEcheanceProche?: number | null;
  montantEcheanceMin?: number | null;
  montantEcheanceMax?: number | null;
  typePenalite?: TypeModeCalcul | null;
  montantPenalite?: number | null;
  pourcentagePenalite?: number | null;
  actif: boolean;
  mouvements: MouvementRegleDto[];
}

export interface CotisationsReglesDto {
  hebdomadaire: RegleOperationDto | null;
  mensuelle: RegleOperationDto | null;
}

export interface EmpruntsReglesDto {
  etale: RegleOperationDto | null;
  caisse: RegleOperationDto | null;
  solidarite: RegleOperationDto | null;
}

export interface UpdateRegleOperationBody {
  libelle: string;
  periodicite: Periodicite | null;
  montantMin: number | null;
  montantMax: number | null;
  montantParPart?: number | null;
  partsMin?: number | null;
  partsMax?: number | null;
  solidariteAuto: boolean;
  montantSolidariteAuto: number | null;
  montantAmendeMin?: number | null;
  montantAmendeMax?: number | null;
  typeFrais?: TypeModeCalcul | null;
  montantFrais?: number | null;
  pourcentageFrais?: number | null;
  nbEcheancesMin?: number | null;
  nbEcheancesMax?: number | null;
  nbEcheancesDefaut?: number | null;
  uniteEcheance?: UniteEcheance | null;
  jourEcheanceMois?: number | null;
  /** Jours avant l'échéance pour l'alerte « proche » (emprunts). */
  joursAlerteEcheanceProche?: number | null;
  montantEcheanceMin?: number | null;
  montantEcheanceMax?: number | null;
  typePenalite?: TypeModeCalcul | null;
  montantPenalite?: number | null;
  pourcentagePenalite?: number | null;
  actif: boolean;
  mouvements: MouvementRegleDto[];
}

@Injectable({ providedIn: 'root' })
export class RegleOperationService {
  private readonly http = inject(HttpClient);

  lister(orgId: number) {
    return this.http.get<RegleOperationDto[]>(`${environment.apiUrl}/organisations/${orgId}/regles`);
  }

  /** Règles actives cotisation hebdo + mensuelle (paramétrage en base). */
  obtenirCotisations(orgId: number) {
    return this.http.get<CotisationsReglesDto>(
      `${environment.apiUrl}/organisations/${orgId}/regles/cotisations`
    );
  }

  /** Règles actives emprunt étalé, caisse et solidarité (paramétrage en base). */
  obtenirEmprunts(orgId: number) {
    return this.http.get<EmpruntsReglesDto>(
      `${environment.apiUrl}/organisations/${orgId}/regles/emprunts`
    );
  }

  mettreAJour(orgId: number, regleId: number, body: UpdateRegleOperationBody) {
    return this.http.put<RegleOperationDto>(
      `${environment.apiUrl}/organisations/${orgId}/regles/${regleId}`,
      body
    );
  }

  basculerActif(orgId: number, regleId: number, actif: boolean) {
    return this.http.patch<RegleOperationDto>(
      `${environment.apiUrl}/organisations/${orgId}/regles/${regleId}/actif`,
      { actif }
    );
  }

  reinitialiser(orgId: number) {
    return this.http.post<RegleOperationDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/regles/reinitialiser`,
      {}
    );
  }
}
