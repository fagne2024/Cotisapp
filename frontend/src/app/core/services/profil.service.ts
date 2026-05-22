import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Role } from '../models/role.model';
import { PosteMembreApi } from '../../features/membres/membres-poste.util';
import { CanalConnexionApi } from './type-profil.service';

export interface ProfilDto {
  userId: number;
  email: string;
  prenom: string;
  nom: string;
  nomComplet: string;
  role: Role;
  roleLabel: string;
  typeProfilId: number | null;
  typeProfilCode: string | null;
  typeProfilLibelle: string | null;
  canalConnexion: CanalConnexionApi;
  identifiantConnexion: string | null;
  organisationId: number | null;
  organisationNom: string | null;
  membreId: number | null;
  codeMembre: string | null;
  posteMembre: PosteMembreApi | null;
  posteLabel: string | null;
  telephone: string | null;
  telephoneSecondaire: string | null;
  adresse: string | null;
  dateAdhesion: string | null;
  dateCreation: string;
  actif: boolean;
  superadminSansOrg: boolean;
  twoFactorEnabled: boolean;
}

export interface ProfilActiviteDto {
  id: number;
  action: string;
  details: string | null;
  libelle: string;
  dateCreation: string;
}

export interface UpdateProfilBody {
  prenom: string;
  nom: string;
  email: string;
  telephone?: string;
  telephoneSecondaire?: string;
  adresse?: string;
}

export interface ChangeMotDePasseBody {
  motDePasseActuel: string;
  nouveauMotDePasse: string;
  confirmationMotDePasse: string;
}

@Injectable({ providedIn: 'root' })
export class ProfilService {
  private readonly http = inject(HttpClient);

  charger() {
    return this.http.get<ProfilDto>(`${environment.apiUrl}/me`);
  }

  activite() {
    return this.http.get<ProfilActiviteDto[]>(`${environment.apiUrl}/me/activite`);
  }

  mettreAJour(body: UpdateProfilBody) {
    return this.http.patch<ProfilDto>(`${environment.apiUrl}/me`, body);
  }

  changerMotDePasse(body: ChangeMotDePasseBody) {
    return this.http.patch<void>(`${environment.apiUrl}/me/mot-de-passe`, body);
  }
}
