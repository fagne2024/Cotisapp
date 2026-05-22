import { Injectable, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';

const ORG_ID_KEY = 'cotisapp_sa_org_id';
const ORG_NOM_KEY = 'cotisapp_sa_org_nom';
const ORG_CODE_KEY = 'cotisapp_sa_org_code';

export interface SuperadminOrgSelection {
  id: number;
  nom: string;
  code?: string;
}

@Injectable({ providedIn: 'root' })
export class SuperadminContextService {
  private readonly auth = inject(AuthService);

  readonly selectedOrgId = signal<number | null>(this.readId());
  readonly selectedOrgNom = signal<string | null>(localStorage.getItem(ORG_NOM_KEY));
  readonly selectedOrgCode = signal<string | null>(localStorage.getItem(ORG_CODE_KEY));

  selectOrg(org: SuperadminOrgSelection): void {
    localStorage.setItem(ORG_ID_KEY, String(org.id));
    localStorage.setItem(ORG_NOM_KEY, org.nom);
    if (org.code) {
      localStorage.setItem(ORG_CODE_KEY, org.code);
    } else {
      localStorage.removeItem(ORG_CODE_KEY);
    }
    this.selectedOrgId.set(org.id);
    this.selectedOrgNom.set(org.nom);
    this.selectedOrgCode.set(org.code ?? null);
    this.auth.mettreAJourSession({
      organisationId: org.id,
      organisationNom: org.nom,
    });
  }

  syncFromRoute(orgId: number, nom?: string | null, code?: string | null): void {
    if (this.selectedOrgId() === orgId) {
      return;
    }
    const storedNom = nom ?? this.selectedOrgNom() ?? `Organisation #${orgId}`;
    this.selectOrg({ id: orgId, nom: storedNom, code: code ?? undefined });
  }

  clearOrg(): void {
    localStorage.removeItem(ORG_ID_KEY);
    localStorage.removeItem(ORG_NOM_KEY);
    localStorage.removeItem(ORG_CODE_KEY);
    this.selectedOrgId.set(null);
    this.selectedOrgNom.set(null);
    this.selectedOrgCode.set(null);
    this.auth.mettreAJourSession({
      organisationId: null,
      organisationNom: null,
    });
  }

  private readId(): number | null {
    const raw = localStorage.getItem(ORG_ID_KEY);
    if (!raw) {
      return null;
    }
    const n = Number(raw);
    return Number.isFinite(n) ? n : null;
  }
}
