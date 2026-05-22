import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { PosteMembreApi } from '../../features/membres/membres-poste.util';
import { Role } from '../models/role.model';

export interface UtilisateurOrgDto {
  utilisateurId: number;
  roleId: number;
  membreId: number | null;
  email: string;
  telephone: string | null;
  nom: string;
  prenom: string;
  nomComplet: string;
  role: 'ADMIN_GIE' | 'MEMBRE';
  poste: PosteMembreApi | null;
  typeProfilCode?: string | null;
  typeProfilLibelle?: string | null;
  codeMembre: string | null;
  actif: boolean;
  derniereConnexionLibelle: string;
  connexions30j: number;
  enLigne: boolean;
}

export interface UtilisateurAccesStatsDto {
  total: number;
  actifs: number;
  suspendus: number;
  connectesMaintenant: number;
}

export interface CreateUtilisateurOrgBody {
  prenom: string;
  nom: string;
  email: string;
  motDePasse?: string;
  role: 'ADMIN_GIE' | 'MEMBRE';
  poste?: PosteMembreApi;
  membreId?: number;
  typeProfilId?: number;
  compteActif: boolean;
  envoyerEmailActivation?: boolean;
}

@Injectable({ providedIn: 'root' })
export class UtilisateurAccesService {
  private readonly http = inject(HttpClient);

  stats(orgId: number) {
    return this.http.get<UtilisateurAccesStatsDto>(
      `${environment.apiUrl}/organisations/${orgId}/utilisateurs-acces/stats`
    );
  }

  lister(orgId: number, role?: Role, actif?: boolean) {
    let params = new HttpParams();
    if (role) params = params.set('role', role);
    if (actif !== undefined) params = params.set('actif', String(actif));
    return this.http.get<UtilisateurOrgDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/utilisateurs-acces`,
      { params }
    );
  }

  creer(orgId: number, body: CreateUtilisateurOrgBody) {
    return this.http.post<UtilisateurOrgDto>(
      `${environment.apiUrl}/organisations/${orgId}/utilisateurs-acces`,
      body
    );
  }

  basculerActif(orgId: number, utilisateurId: number, actif: boolean) {
    return this.http.patch<UtilisateurOrgDto>(
      `${environment.apiUrl}/organisations/${orgId}/utilisateurs-acces/${utilisateurId}/actif`,
      { actif }
    );
  }
}
