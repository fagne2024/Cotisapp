import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export type NiveauDroitApi = 'OK' | 'NO' | 'LIM' | 'OWN';

export interface MesDroitsDto {
  peutGestion: boolean;
  actions: Record<string, NiveauDroitApi>;
}

@Injectable({ providedIn: 'root' })
export class DroitAccesService {
  private readonly http = inject(HttpClient);

  private readonly _droits = signal<MesDroitsDto | null>(null);
  readonly droits = this._droits.asReadonly();

  chargerEtMemoriser(orgId: number) {
    return this.http.get<MesDroitsDto>(`${environment.apiUrl}/organisations/${orgId}/mes-droits`);
  }

  setDroits(d: MesDroitsDto | null): void {
    this._droits.set(d);
  }

  clear(): void {
    this._droits.set(null);
  }

  peutGestion(orgId: number | null): boolean {
    if (orgId == null) return false;
    return this._droits()?.peutGestion ?? false;
  }

  peutAction(orgId: number | null, code: string): boolean {
    if (orgId == null) return false;
    const n = this._droits()?.actions?.[code];
    return n != null && n !== 'NO';
  }
}
