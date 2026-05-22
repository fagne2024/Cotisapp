import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { CreateCompteModeleMembreBody } from './compte-modele-membre.service';

export interface OrganisationDto {
  id: number;
  code: string;
  nom: string;
  description?: string | null;
  actif: boolean;
  logoUrl?: string | null;
}

export interface ComptesOrganisationSelection {
  solidarite: boolean;
  epargneHebdo: boolean;
  epargneMois: boolean;
  penalite: boolean;
  amende: boolean;
  banque: boolean;
}

export interface AdminGieCreationBody {
  prenom: string;
  nom: string;
  email: string;
  motDePasse?: string;
}

export interface AdminGieUpsertBody extends AdminGieCreationBody {
  compteActif?: boolean;
  forcerChangementMotDePasse?: boolean;
}

export interface CreateOrganisationBody {
  code: string;
  nom: string;
  description?: string;
  comptes: ComptesOrganisationSelection;
  modelesComptePersonnalises: CreateCompteModeleMembreBody[];
  administrateurGie: AdminGieCreationBody;
}

export interface UpdateOrganisationBody {
  nom: string;
  description?: string;
  actif?: boolean;
}

@Injectable({ providedIn: 'root' })
export class OrganisationService {
  private readonly http = inject(HttpClient);

  lister() {
    return this.http.get<OrganisationDto[]>(`${environment.apiUrl}/organisations`);
  }

  creer(body: CreateOrganisationBody) {
    return this.http.post<OrganisationDto>(`${environment.apiUrl}/organisations`, body);
  }

  modifier(id: number, body: UpdateOrganisationBody) {
    return this.http.put<OrganisationDto>(`${environment.apiUrl}/organisations/${id}`, body);
  }

  supprimer(id: number) {
    return this.http.delete<void>(`${environment.apiUrl}/organisations/${id}`);
  }

  uploadLogo(orgId: number, fichier: File) {
    const form = new FormData();
    form.append('logo', fichier, fichier.name);
    return this.http.post<OrganisationDto>(`${environment.apiUrl}/organisations/${orgId}/logo`, form);
  }

  telechargerLogo(orgId: number) {
    return this.http.get(`${environment.apiUrl}/organisations/${orgId}/logo`, {
      responseType: 'blob',
    });
  }

  enregistrerAdminGie(orgId: number, body: AdminGieUpsertBody) {
    return this.http.put<void>(`${environment.apiUrl}/organisations/${orgId}/admin-gie`, body);
  }
}
