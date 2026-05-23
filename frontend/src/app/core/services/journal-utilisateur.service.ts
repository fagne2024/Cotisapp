import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export type TypeEvenementJournal =
  | 'CONNEXION'
  | 'DECONNEXION'
  | 'CONNEXION_ECHEC'
  | 'MODULE_VISITE'
  | 'ACTION_METIER'
  | 'NAVIGATION'
  | 'SECURITE';

export interface JournalUtilisateurDto {
  id: number;
  organisationId: number | null;
  utilisateurId: number | null;
  utilisateurEmail: string | null;
  utilisateurNom: string | null;
  role: string | null;
  membreId: number | null;
  action: string;
  typeEvenement: TypeEvenementJournal;
  typeEvenementLibelle: string;
  moduleCode: string | null;
  moduleLibelle: string | null;
  routePath: string | null;
  details: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  navigateurResume: string | null;
  succes: boolean;
  dateCreation: string;
  libelleResume: string;
}

export interface JournalPageDto {
  content: JournalUtilisateurDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface EnregistrerEvenementJournalBody {
  typeEvenement: TypeEvenementJournal;
  action: string;
  moduleCode?: string;
  moduleLibelle?: string;
  routePath?: string;
  details?: string;
  succes?: boolean;
}

export const TYPES_EVENEMENT_JOURNAL: { value: TypeEvenementJournal | ''; label: string }[] = [
  { value: '', label: 'Tous les types' },
  { value: 'CONNEXION', label: 'Connexion' },
  { value: 'DECONNEXION', label: 'Déconnexion' },
  { value: 'CONNEXION_ECHEC', label: 'Échec connexion' },
  { value: 'MODULE_VISITE', label: 'Module visité' },
  { value: 'ACTION_METIER', label: 'Action métier' },
  { value: 'NAVIGATION', label: 'Navigation' },
  { value: 'SECURITE', label: 'Sécurité' },
];

@Injectable({ providedIn: 'root' })
export class JournalUtilisateurService {
  private readonly http = inject(HttpClient);

  lister(
    orgId: number,
    options?: {
      utilisateurId?: number;
      type?: TypeEvenementJournal;
      succes?: boolean;
      search?: string;
      page?: number;
      size?: number;
    }
  ) {
    let params = new HttpParams()
      .set('page', String(options?.page ?? 0))
      .set('size', String(options?.size ?? 30));
    if (options?.utilisateurId != null) {
      params = params.set('utilisateurId', String(options.utilisateurId));
    }
    if (options?.type) {
      params = params.set('type', options.type);
    }
    if (options?.succes != null) {
      params = params.set('succes', String(options.succes));
    }
    if (options?.search?.trim()) {
      params = params.set('search', options.search.trim());
    }
    return this.http.get<JournalPageDto>(
      `${environment.apiUrl}/organisations/${orgId}/journal-utilisateur`,
      { params }
    );
  }

  compter(orgId: number) {
    return this.http.get<{ total: number }>(
      `${environment.apiUrl}/organisations/${orgId}/journal-utilisateur/count`
    );
  }

  enregistrerEvenement(orgId: number, body: EnregistrerEvenementJournalBody) {
    return this.http.post<void>(
      `${environment.apiUrl}/organisations/${orgId}/journal-utilisateur/evenement`,
      body
    );
  }
}
