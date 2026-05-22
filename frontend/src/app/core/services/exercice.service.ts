import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export type StatutExercice = 'EN_COURS' | 'CLOTURE';

export type StatutPlanad = 'OUVERT' | 'CLOTURE';

export interface ExerciceDto {
  id: number;
  organisationId: number;
  numero: number;
  statut: StatutExercice;
  dateDebut: string;
  dateCloture?: string | null;
  planadFin?: number | null;
  reinitialisationComptes: boolean;
  observationCloture?: string | null;
  courant: boolean;
  nbPlanads: number;
  nbPlanadsOuverts: number;
  tousPlanadsClotures: boolean;
  planadOuvertLibelle?: string | null;
}

export interface OuvrirExerciceBody {
  reinitialiserComptes?: boolean;
  observationCloture?: string;
  effectuerRepartition?: boolean;
}

@Injectable({ providedIn: 'root' })
export class ExerciceService {
  private readonly http = inject(HttpClient);

  lister(orgId: number) {
    return this.http.get<ExerciceDto[]>(`${environment.apiUrl}/organisations/${orgId}/exercices`);
  }

  courant(orgId: number) {
    return this.http.get<ExerciceDto>(`${environment.apiUrl}/organisations/${orgId}/exercices/courant`);
  }

  transition(orgId: number, body: OuvrirExerciceBody) {
    return this.http.post<ExerciceDto>(
      `${environment.apiUrl}/organisations/${orgId}/exercices/transition`,
      body
    );
  }

  reouvrir(orgId: number, exerciceId: number) {
    return this.http.post<ExerciceDto>(
      `${environment.apiUrl}/organisations/${orgId}/exercices/${exerciceId}/reouvrir`,
      {}
    );
  }
}
