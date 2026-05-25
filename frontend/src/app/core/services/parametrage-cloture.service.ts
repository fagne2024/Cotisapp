import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { TypeModeCalcul } from './regle-operation.service';

export type ModeRepartitionCloture = 'PRORATA' | 'EQUITABLE';
export type ModeAgregationPostesCloture = 'SEPARER' | 'ADDITIONNER' | 'GROUPES';
export type ModeCalculProrataCloture = 'PARTS' | 'POURCENTAGE';
export type TypeCompteCloture =
  | 'EPARGNE_HEBDO'
  | 'EPARGNE_MOIS'
  | 'SOLIDARITE'
  | 'PENALITE'
  | 'AMENDE'
  | 'INTERET'
  | 'CAISSE'
  | 'BANQUE';

export type TypeOperationCloture =
  | 'COTISATION'
  | 'COTISATION_MOIS'
  | 'EMPRUNT'
  | 'REMBOURSEMENT'
  | 'PENALITE'
  | 'AMENDE'
  | 'DEPENSE'
  | 'DEPOT_BANQUE'
  | 'RETRAIT_BANQUE';

export interface PostePartageClotureDto {
  code: string;
  libelle: string;
  actif: boolean;
  builtIn: boolean;
  compteMembre: TypeCompteCloture;
  compteSourceOrg: TypeCompteCloture;
  typeOperation?: TypeOperationCloture | null;
  groupePartage?: number | null;
  /** En mode ADDITIONNER : inclure dans le pool additionné. */
  inclureDansPoolAdditionne?: boolean;
  /** En mode PRORATA : appliquer parts / % sur ce poste. */
  appliquerProrata?: boolean;
  montantPool?: number;
  montantDistribue?: number;
}

export interface MembrePourcentageRepartitionDto {
  membreId: number;
  codeMembre?: string;
  nomComplet?: string;
  pourcentage: number;
}

export interface RetenueClotureDto {
  libelle: string;
  typeMode: TypeModeCalcul;
  valeur: number;
  ordre: number;
  montantCalcule?: number;
}

export interface ParametrageClotureDto {
  organisationId: number;
  cotisationMontantMin: number;
  cotisationMontantMax: number;
  partsMin: number;
  partsMax: number;
  partagerInterets: boolean;
  partagerPenalites: boolean;
  partagerAmendes: boolean;
  modeRepartition: ModeRepartitionCloture;
  modeAgregationPostes?: ModeAgregationPostesCloture;
  modeCalculProrata?: ModeCalculProrataCloture;
  pourcentagesRepartition?: MembrePourcentageRepartitionDto[];
  exclureMembresPretEnCours?: boolean;
  postesPartage: PostePartageClotureDto[];
  fraisClotureType: TypeModeCalcul;
  fraisClotureValeur: number;
  retenues: RetenueClotureDto[];
  compteVersementMembre: TypeCompteCloture;
  compteSourceOrg: 'CAISSE' | 'SOLIDARITE' | 'BANQUE';
}

export interface MembreRepartitionClotureDto {
  membreId: number;
  codeMembre: string;
  nomComplet: string;
  nombreParts: number;
  montantCotisationsExercice: number;
  montantPart: number;
  montantInterets?: number;
  montantPenalites?: number;
  montantAmendes?: number;
  montantsParPoste?: Record<string, number>;
  pourcentageRepartition?: number | null;
  excluDuPartage?: boolean;
  motifExclusion?: string | null;
}

export interface PreviewClotureExerciceDto {
  exerciceId: number;
  exerciceNumero: number;
  poolInterets: number;
  poolPenalites: number;
  poolAmendes: number;
  modeRepartition: ModeRepartitionCloture;
  modeAgregationPostes?: ModeAgregationPostesCloture;
  modeCalculProrata?: ModeCalculProrataCloture;
  exclureMembresPretEnCours?: boolean;
  postes: PostePartageClotureDto[];
  poolBrut: number;
  fraisCloture: number;
  retenues: RetenueClotureDto[];
  totalRetenues: number;
  netADistribuer: number;
  totalParts: number;
  membres: MembreRepartitionClotureDto[];
}

export const POSTES_PARTAGE_DEFAUT: PostePartageClotureDto[] = [
  {
    code: 'INTERETS',
    libelle: "Intérêts / frais d'emprunt collectés",
    actif: true,
    builtIn: true,
    compteMembre: 'INTERET',
    compteSourceOrg: 'INTERET',
    groupePartage: 1,
    inclureDansPoolAdditionne: true,
    appliquerProrata: true,
  },
  {
    code: 'PENALITES',
    libelle: 'Pénalités de retard (remboursements)',
    actif: true,
    builtIn: true,
    compteMembre: 'PENALITE',
    compteSourceOrg: 'CAISSE',
    groupePartage: 2,
    inclureDansPoolAdditionne: true,
    appliquerProrata: true,
  },
  {
    code: 'AMENDES',
    libelle: 'Amendes sur cotisations',
    actif: true,
    builtIn: true,
    compteMembre: 'AMENDE',
    compteSourceOrg: 'CAISSE',
    groupePartage: 2,
    inclureDansPoolAdditionne: true,
    appliquerProrata: true,
  },
];

export type PerimetrePartagePreset = 'INTERETS_SEUL' | 'SANCTIONS_SEUL' | 'TOUS';

export const BUILTIN_POSTES_CLOTURE = ['INTERETS', 'PENALITES', 'AMENDES'] as const;
export type BuiltinPosteCloture = (typeof BUILTIN_POSTES_CLOTURE)[number];

@Injectable({ providedIn: 'root' })
export class ParametrageClotureService {
  private readonly http = inject(HttpClient);

  get(orgId: number) {
    return this.http.get<ParametrageClotureDto>(
      `${environment.apiUrl}/organisations/${orgId}/parametrage-cloture`
    );
  }

  enregistrer(orgId: number, body: ParametrageClotureDto) {
    return this.http.put<ParametrageClotureDto>(
      `${environment.apiUrl}/organisations/${orgId}/parametrage-cloture`,
      body
    );
  }

  previewRepartition(orgId: number) {
    return this.http.get<PreviewClotureExerciceDto>(
      `${environment.apiUrl}/organisations/${orgId}/exercices/courant/preview-repartition`
    );
  }

  /** Aperçu selon le brouillon du formulaire (sans enregistrer). */
  previewRepartitionAvecParametrage(orgId: number, body: ParametrageClotureDto) {
    return this.http.post<PreviewClotureExerciceDto>(
      `${environment.apiUrl}/organisations/${orgId}/parametrage-cloture/preview-repartition`,
      body
    );
  }
}
