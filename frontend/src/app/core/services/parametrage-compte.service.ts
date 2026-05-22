import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export type FamilleCompte =
  | 'CAISSE'
  | 'SOLIDARITE'
  | 'EPARGNE_HEBDO'
  | 'EPARGNE_MOIS'
  | 'PENALITE'
  | 'AMENDE';

export interface ParametrageCompteDto {
  famille: FamilleCompte;
  libelle: string;
  typeCompte: string;
  proprietaire: 'ORGANISATION' | 'MEMBRE';
  actif: boolean;
  soldeOrganisation: number | null;
  description: string;
}

export interface UpdateParametrageCompteItem {
  libelle: string;
  actif: boolean;
}

export type UpdateParametrageComptesBody = {
  comptes: Partial<Record<FamilleCompte, UpdateParametrageCompteItem>>;
};

@Injectable({ providedIn: 'root' })
export class ParametrageCompteService {
  private readonly http = inject(HttpClient);

  lister(orgId: number) {
    return this.http.get<ParametrageCompteDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/parametrage-comptes`
    );
  }

  mettreAJour(orgId: number, body: UpdateParametrageComptesBody) {
    return this.http.put<ParametrageCompteDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/parametrage-comptes`,
      body
    );
  }
}
