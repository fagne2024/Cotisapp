import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export type StatutSuivi = 'NON_PAYE' | 'PARTIEL' | 'PAYE';

export interface SuiviMensuelDto {
  id: number;
  membreId: number;
  membreNom: string;
  codeMembre: string;
  moisAnnee: string;
  montantDu: number;
  montantPaye: number;
  statut: StatutSuivi;
}

@Injectable({ providedIn: 'root' })
export class SuiviMensuelService {
  private readonly http = inject(HttpClient);

  lister(orgId: number, mois: string) {
    return this.http.get<SuiviMensuelDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/suivi-mensuel`,
      { params: { mois } }
    );
  }

  generer(orgId: number, mois: string) {
    return this.http.post<{ mois: string; cree: number }>(
      `${environment.apiUrl}/organisations/${orgId}/suivi-mensuel/generer`,
      null,
      { params: { mois } }
    );
  }
}
