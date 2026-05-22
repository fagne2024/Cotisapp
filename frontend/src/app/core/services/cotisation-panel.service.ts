import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface CotisationSuiviMembreDto {
  membreId: number;
  nomComplet: string;
  codeMembre: string;
  poste?: string | null;
  sousTitre: string;
  statut: 'PAYE' | 'ATTENTE';
}

export interface CotisationRecenteDto {
  membreNom: string;
  libelle: string;
  meta: string;
  montant: number;
  iconeClass: string;
}

export interface CotisationHistoriqueLigneDto {
  ligneId: string;
  operationId: number;
  typeLigne: 'HEBDO' | 'MOIS' | 'SOLIDARITE';
  typeCotisation: 'HEBDO' | 'MOIS';
  typeLibelle: string;
  membreId: number | null;
  membreNom: string;
  codeMembre: string;
  periode: string;
  montant: number;
  dateOperation: string;
  dateLabel: string;
  observation?: string | null;
  modePaiementLibelle?: string | null;
  referencePaiement?: string | null;
  annulee: boolean;
  annulable: boolean;
}

export interface CotisationAnnulationDto {
  operationOrigineId: number;
  operationAnnulationId: number;
  message: string;
  dateAnnulation: string;
  mouvementsInverses: number;
}

export interface CotisationPanneauDto {
  periodeLabel: string;
  suivi: CotisationSuiviMembreDto[];
  recentes: CotisationRecenteDto[];
  cotisationsAujourdhui: number;
  montantAujourdhui: number;
}

@Injectable({ providedIn: 'root' })
export class CotisationPanelService {
  private readonly http = inject(HttpClient);

  chargerPanneau(
    orgId: number,
    type: 'hebdo' | 'mois',
    params: { semaine?: string; mois?: string }
  ) {
    const httpParams: Record<string, string> = { t: type };
    if (type === 'hebdo' && params.semaine) {
      httpParams['semaine'] = params.semaine;
    }
    if (type === 'mois' && params.mois) {
      httpParams['mois'] = params.mois;
    }
    return this.http.get<CotisationPanneauDto>(
      `${environment.apiUrl}/organisations/${orgId}/cotisations/panneau`,
      { params: httpParams }
    );
  }

  listerHistorique(orgId: number) {
    return this.http.get<CotisationHistoriqueLigneDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/cotisations/historique`
    );
  }

  annulerOperation(orgId: number, operationId: number) {
    return this.http.post<CotisationAnnulationDto>(
      `${environment.apiUrl}/organisations/${orgId}/cotisations/operations/${operationId}/annuler`,
      {}
    );
  }
}
