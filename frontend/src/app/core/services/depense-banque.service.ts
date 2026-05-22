import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface DepenseLigneDto {
  id: number;
  categorieId: string;
  categorieLabel: string;
  montant: number;
  dateOperation: string;
  beneficiaire?: string;
  description?: string;
}

export interface DepenseParCategorieDto {
  categorieId: string;
  icon: string;
  label: string;
  montant: number;
}

export interface MouvementCaisseLigneDto {
  id: number;
  dateOperation: string;
  sens: 'credit' | 'debit';
  montant: number;
  soldeCaisseApres: number;
  typeOperation: string;
  libelle: string;
}

export interface MouvementBanqueLigneDto {
  id: number;
  dateOperation: string;
  type: 'vers' | 'ret';
  montant: number;
  soldeBanqueApres: number;
  reference?: string;
  description?: string;
  releveId?: number;
  releveNomFichier?: string;
}

export interface DepenseBanqueDashboardDto {
  soldeCaisse: number;
  soldeBanque: number;
  totalDepensesMois: number;
  depensesRecentes: DepenseLigneDto[];
  depensesParCategorie: DepenseParCategorieDto[];
  mouvementsBanque: MouvementBanqueLigneDto[];
  entreesCaisseMois: number;
  sortiesCaisseMois: number;
  mouvementsCaisse: MouvementCaisseLigneDto[];
}

export interface DepenseRequestBody {
  montant: number;
  compteDebite: string;
  beneficiaire?: string;
  dateDepense: string;
  description?: string;
  categorieId: string;
}

export interface BanqueMouvementRequestBody {
  montant: number;
  type: 'vers' | 'ret';
  dateOperation: string;
  reference?: string;
  banqueAgence?: string;
  description?: string;
  contreSigne?: string;
}

@Injectable({ providedIn: 'root' })
export class DepenseBanqueService {
  private readonly http = inject(HttpClient);

  chargerTableauDeBord(orgId: number) {
    return this.http.get<DepenseBanqueDashboardDto>(
      `${environment.apiUrl}/organisations/${orgId}/depenses-banque`
    );
  }

  enregistrerDepense(orgId: number, body: DepenseRequestBody) {
    return this.http.post<{ id: number }>(
      `${environment.apiUrl}/organisations/${orgId}/depenses-banque/depenses`,
      body
    );
  }

  enregistrerBanque(orgId: number, body: BanqueMouvementRequestBody, releve?: File) {
    const fd = new FormData();
    fd.append(
      'data',
      new Blob([JSON.stringify(body)], { type: 'application/json' })
    );
    if (releve) {
      fd.append('releve', releve, releve.name);
    }
    return this.http.post<{ id: number }>(
      `${environment.apiUrl}/organisations/${orgId}/depenses-banque/banque`,
      fd
    );
  }

  telechargerReleve(orgId: number, releveId: number) {
    return this.http.get(
      `${environment.apiUrl}/organisations/${orgId}/depenses-banque/releves/${releveId}`,
      { responseType: 'blob' }
    );
  }
}
