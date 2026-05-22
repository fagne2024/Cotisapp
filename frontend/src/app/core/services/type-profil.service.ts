import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Role } from '../models/role.model';
import { PosteMembreApi } from '../../features/membres/membres-poste.util';

export type CanalConnexionApi = 'EMAIL' | 'TELEPHONE' | 'LES_DEUX';
export type NiveauDroitApi = 'OK' | 'NO' | 'LIM' | 'OWN';

export interface TypeProfilDto {
  id: number;
  organisationId: number | null;
  code: string;
  libelle: string;
  role: Role;
  posteMembre: PosteMembreApi | null;
  canalConnexion: CanalConnexionApi;
  actif: boolean;
  ordre: number;
  systeme?: boolean;
}

export interface TypeProfilDroitDto {
  actionCode: string;
  section: string | null;
  libelle: string;
  niveau: NiveauDroitApi;
}

export interface CreateTypeProfilBody {
  code: string;
  libelle: string;
  role: Role;
  posteMembre?: PosteMembreApi | null;
  canalConnexion?: CanalConnexionApi;
  actif?: boolean;
  ordre?: number;
}

export interface UpdateTypeProfilBody {
  libelle?: string;
  posteMembre?: PosteMembreApi | null;
  canalConnexion?: CanalConnexionApi;
  actif?: boolean;
  ordre?: number;
}

@Injectable({ providedIn: 'root' })
export class TypeProfilService {
  private readonly http = inject(HttpClient);
  private readonly base = (orgId: number) => `${environment.apiUrl}/organisations/${orgId}/types-profil`;

  lister(orgId: number) {
    return this.http.get<TypeProfilDto[]>(this.base(orgId));
  }

  listerGestion(orgId: number) {
    return this.http.get<TypeProfilDto[]>(`${this.base(orgId)}/gestion`);
  }

  creer(orgId: number, body: CreateTypeProfilBody) {
    return this.http.post<TypeProfilDto>(this.base(orgId), body);
  }

  modifier(orgId: number, typeProfilId: number, body: UpdateTypeProfilBody) {
    return this.http.patch<TypeProfilDto>(`${this.base(orgId)}/${typeProfilId}`, body);
  }

  supprimer(orgId: number, typeProfilId: number) {
    return this.http.delete<void>(`${this.base(orgId)}/${typeProfilId}`);
  }

  listerDroits(orgId: number, typeProfilId: number) {
    return this.http.get<TypeProfilDroitDto[]>(`${this.base(orgId)}/${typeProfilId}/droits`);
  }

  sauvegarderDroits(orgId: number, typeProfilId: number, droits: { actionCode: string; niveau: NiveauDroitApi }[]) {
    return this.http.put<TypeProfilDroitDto[]>(`${this.base(orgId)}/${typeProfilId}/droits`, { droits });
  }

  /** Réapplique les matrices SG / SGA / Trésorier / Superviseur / etc. */
  reinitialiserDroitsSysteme(orgId: number) {
    return this.http.post<void>(`${this.base(orgId)}/systeme/reinitialiser-droits`, {});
  }
}
