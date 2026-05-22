import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface SuperadminKpiDto {
  organisationsActives: number;
  totalMembres: number;
  caisseTotale: number;
  empruntsActifs: number;
  empruntsEnRetard: number;
  solidariteTotale: number;
}

export interface OrganisationResumeDto {
  id: number;
  code: string;
  nom: string;
  description?: string | null;
  logoUrl?: string | null;
  actif: boolean;
  dateCreation?: string | null;
  adminUtilisateurId?: number | null;
  adminPrenom?: string | null;
  adminNom: string;
  adminEmail: string;
  adminActif?: boolean;
  adminTwoFactorEnabled?: boolean;
  nbMembres: number;
  nbMembresBureau: number;
  nbMembresSimples: number;
  soldeCaisse: number;
  soldeSolidarite: number;
  soldeBanque: number;
  nbEmpruntsActifs: number;
  nbEmpruntsEnRetard: number;
  nbRegles: number;
}

export interface SuperadminActiviteDto {
  icone: string;
  fondCouleur: string;
  libelle: string;
  meta: string;
  montant: number;
  credit: boolean;
}

export interface CotisationOrgChartDto {
  code: string;
  nom: string;
  montant: number;
}

export interface SuperadminVueGlobaleDto {
  kpi: SuperadminKpiDto;
  organisations: OrganisationResumeDto[];
  cotisationsParOrganisation: CotisationOrgChartDto[];
  activiteRecente: SuperadminActiviteDto[];
}

@Injectable({ providedIn: 'root' })
export class SuperadminService {
  private readonly http = inject(HttpClient);

  vueGlobale() {
    return this.http.get<SuperadminVueGlobaleDto>(`${environment.apiUrl}/superadmin/vue-globale`);
  }

  reinitialiserMotDePasseAdminGie(
    orgId: number,
    body: { motDePasse?: string; forcerChangement?: boolean }
  ) {
    return this.http.put<void>(
      `${environment.apiUrl}/superadmin/organisations/${orgId}/admin-gie/mot-de-passe`,
      body
    );
  }

  reinitialiserTwoFactorAdminGie(orgId: number) {
    return this.http.post<void>(
      `${environment.apiUrl}/superadmin/organisations/${orgId}/admin-gie/2fa/reinitialiser`,
      {}
    );
  }
}
