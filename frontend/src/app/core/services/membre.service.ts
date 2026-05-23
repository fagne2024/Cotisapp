import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { PosteMembreApi } from '../../features/membres/membres-poste.util';
import { EmpruntDto } from './emprunt.service';
import {
  JourneeReunionDto,
  RecapMembreDto,
  RecapJourneeSyntheseDto,
  RecapOperationLigneDto,
} from './recap-journee.service';
import { SuiviMensuelDto } from './suivi-mensuel.service';

export interface MembreDto {
  id: number;
  codeMembre: string;
  nom: string;
  prenom: string;
  nomComplet: string;
  actif: boolean;
  dateCreation?: string;
  telephone?: string | null;
  email?: string | null;
  poste?: PosteMembreApi;
  dateAdhesion?: string | null;
  pieceIdentite?: string | null;
  utilisateurId?: number | null;
  compteAcces?: boolean;
  /** Cotisations / remboursements mobile money depuis « Mon compte » (activé par l'admin GIE). */
  paiementMobileActif?: boolean;
}

export interface ComptesMembreSelectionBody {
  epargneHebdo: boolean;
  epargneMois: boolean;
  solidarite: boolean;
  penalite: boolean;
  amende: boolean;
}

export interface CreateMembreBody {
  prenom: string;
  nom: string;
  email?: string;
  telephone?: string;
  dateAdhesion?: string;
  pieceIdentite?: string;
  poste: PosteMembreApi;
  comptes: ComptesMembreSelectionBody;
  modelesCompteIds?: number[];
  creerCompteAcces?: boolean;
  envoyerEmailActivation?: boolean;
  typeProfilId?: number;
  paiementMobileActif?: boolean;
}

export interface UpdateMembreBody {
  prenom: string;
  nom: string;
  email?: string;
  telephone?: string;
  dateAdhesion?: string;
  pieceIdentite?: string;
  poste: PosteMembreApi;
  actif: boolean;
  paiementMobileActif?: boolean;
}

@Injectable({ providedIn: 'root' })
export class MembreService {
  private readonly http = inject(HttpClient);

  /** @param tous Si vrai, inclut les membres inactifs (suspendus / exclus). */
  lister(orgId: number, tous = false) {
    return this.http.get<MembreDto[]>(`${environment.apiUrl}/organisations/${orgId}/membres`, {
      params: tous ? { tous: 'true' } : {},
    });
  }

  /** Recherche par code (partiel), nom, prénom ou téléphone. */
  rechercher(orgId: number, q: string) {
    return this.http.get<MembreDto[]>(`${environment.apiUrl}/organisations/${orgId}/membres/recherche`, {
      params: { q: q.trim() },
    });
  }

  listerSoldesComptes(orgId: number) {
    return this.http.get<MembreSoldesDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/membres/soldes-comptes`
    );
  }

  get(orgId: number, membreId: number) {
    return this.http.get<MembreDto>(`${environment.apiUrl}/organisations/${orgId}/membres/${membreId}`);
  }

  creer(orgId: number, body: CreateMembreBody) {
    return this.http.post<MembreDto>(`${environment.apiUrl}/organisations/${orgId}/membres`, body);
  }

  modifier(orgId: number, membreId: number, body: UpdateMembreBody) {
    return this.http.put<MembreDto>(
      `${environment.apiUrl}/organisations/${orgId}/membres/${membreId}`,
      body
    );
  }

  bulkPaiementMobile(orgId: number, membreIds: number[], actif: boolean) {
    return this.http.patch<{ nombreMisAJour: number; actif: boolean; message: string }>(
      `${environment.apiUrl}/organisations/${orgId}/membres/paiement-mobile`,
      { membreIds, actif }
    );
  }

  supprimer(orgId: number, membreId: number) {
    return this.http.delete<void>(`${environment.apiUrl}/organisations/${orgId}/membres/${membreId}`);
  }

  listerComptes(orgId: number, membreId: number) {
    return this.http.get<CompteMembreDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/membres/${membreId}/comptes`
    );
  }

  /** Solde net : épargne + solidarité − emprunts − frais emprunt + remboursements + frais remboursement. */
  obtenirSoldeMembre(orgId: number, membreId: number) {
    return this.http.get<MembreSoldeMembreDto>(
      `${environment.apiUrl}/organisations/${orgId}/membres/${membreId}/solde`
    );
  }

  listerOperations(orgId: number, membreId: number) {
    return this.http.get<OperationMembreDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/membres/${membreId}/operations`
    );
  }

  listerRecapJourneesMonCompte(orgId: number) {
    return this.http.get<JourneeReunionDto[]>(
      `${environment.apiUrl}/organisations/${orgId}/mon-compte/recap-journees`
    );
  }

  obtenirRecapJourneeMonCompte(orgId: number, journeeId: number) {
    return this.http.get<RecapMembreJourneeDto>(
      `${environment.apiUrl}/organisations/${orgId}/mon-compte/recap-journees/${journeeId}`
    );
  }

  obtenirRecapJourneeMonCompteParDate(orgId: number, date: string) {
    return this.http.get<RecapMembreJourneeDto>(
      `${environment.apiUrl}/organisations/${orgId}/mon-compte/recap-journees/par-date`,
      { params: { date } }
    );
  }

  /** Fiche agrégée pour la page « Mon compte » (rôle MEMBRE). */
  chargerMonCompte(orgId: number, mois?: string) {
    const url = `${environment.apiUrl}/organisations/${orgId}/mon-compte`;
    if (mois) {
      return this.http.get<MonCompteFicheDto>(url, { params: { mois } });
    }
    return this.http.get<MonCompteFicheDto>(url);
  }

  telechargerModeleImport(orgId: number) {
    return this.http.get(`${environment.apiUrl}/organisations/${orgId}/membres/import/modele`, {
      responseType: 'blob',
    });
  }

  importerFichier(orgId: number, fichier: File) {
    const form = new FormData();
    form.append('fichier', fichier, fichier.name);
    return this.http.post<ImportMembresResult>(
      `${environment.apiUrl}/organisations/${orgId}/membres/import`,
      form
    );
  }
}

export type TypeCompteMembreApi =
  | 'EPARGNE'
  | 'EPARGNE_HEBDO'
  | 'EPARGNE_MOIS'
  | 'SOLIDARITE'
  | 'PENALITE'
  | 'AMENDE'
  | 'CUSTOM';

export interface CompteMembreDto {
  id: number;
  typeCompte: TypeCompteMembreApi;
  libelle: string;
  solde: number;
  modeleCompteId?: number | null;
  modeleCode?: string | null;
}

export type TypeOperationApi =
  | 'COTISATION'
  | 'COTISATION_MOIS'
  | 'VERSEMENT'
  | 'EMPRUNT'
  | 'REMBOURSEMENT'
  | 'PENALITE'
  | 'AMENDE'
  | 'DEPENSE'
  | 'BANQUE_VERSEMENT'
  | 'BANQUE_RETRAIT';

export interface OperationMembreDto {
  id: number;
  typeOperation: TypeOperationApi;
  membreId?: number | null;
  membreNom?: string | null;
  montant: number;
  montantFrais?: number | null;
  /** Part solidarité (cotisation hebdo / mois). */
  montantSolidarite?: number | null;
  empruntId?: number | null;
  dateOperation: string;
  moisAnnee?: string | null;
  observation?: string | null;
}

export interface MembreSoldesDto {
  membreId: number;
  epargneHebdo: number;
  epargneMois: number;
  solidarite: number;
  penalite: number;
  amende: number;
}

export interface RecapMembreJourneeDto {
  journeeId: number;
  libelle: string;
  numero: number;
  dateReunion: string;
  resume: RecapMembreDto;
  synthese: RecapJourneeSyntheseDto;
  operations: RecapOperationLigneDto[];
}

export interface MonCompteFicheDto {
  membre: MembreDto;
  comptes: CompteMembreDto[];
  operations: OperationMembreDto[];
  emprunts: EmpruntDto[];
  suiviMensuel: SuiviMensuelDto | null;
  solde: MembreSoldeMembreDto;
}

export interface MembreSoldeMembreDto {
  membreId: number;
  solde: number;
  epargne: number;
  solidarite: number;
  emprunts: number;
  fraisEmprunt: number;
  remboursements: number;
  fraisRemboursement: number;
}

export interface ImportMembreLigneErreur {
  ligne: number;
  message: string;
}

export interface ImportMembresResult {
  lignesLues: number;
  membresCrees: number;
  erreurs: ImportMembreLigneErreur[];
}
