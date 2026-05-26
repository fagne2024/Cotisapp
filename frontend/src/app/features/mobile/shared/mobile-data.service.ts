import { Injectable, computed, inject, signal } from '@angular/core';
import { MonCompteFicheDto } from '../../../core/services/membre.service';
import { MembreService } from '../../../core/services/membre.service';

@Injectable({ providedIn: 'root' })
export class MobileDataService {
  private readonly membreService = inject(MembreService);

  private readonly _fiche = signal<MonCompteFicheDto | null>(null);
  private readonly _loading = signal(false);
  private readonly _error = signal(false);
  private _loadedOrgId: number | null = null;

  readonly fiche = this._fiche.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  readonly membre = computed(() => this._fiche()?.membre ?? null);
  readonly comptes = computed(() => this._fiche()?.comptes ?? []);
  readonly operations = computed(() => this._fiche()?.operations ?? []);
  readonly emprunts = computed(() => this._fiche()?.emprunts ?? []);
  readonly solde = computed(() => this._fiche()?.solde ?? null);

  readonly epargne = computed(() => {
    const s = this._fiche()?.solde;
    return s ? s.epargne : 0;
  });

  readonly empruntsEnCours = computed(() =>
    (this._fiche()?.emprunts ?? []).filter(
      (e) => e.statut === 'EN_COURS' || e.statut === 'RETARD',
    ),
  );

  readonly operationsRecentes = computed(() =>
    (this._fiche()?.operations ?? []).slice(0, 8),
  );

  charger(orgId: number, force = false): void {
    if (!force && this._loadedOrgId === orgId && this._fiche() !== null) return;
    this._loading.set(true);
    this._error.set(false);
    this._loadedOrgId = orgId;
    this.membreService.chargerMonCompte(orgId).subscribe({
      next: (fiche) => {
        this._fiche.set(fiche);
        this._loading.set(false);
      },
      error: () => {
        this._error.set(true);
        this._loading.set(false);
      },
    });
  }

  rafraichir(orgId: number): void {
    this.charger(orgId, true);
  }

  reset(): void {
    this._fiche.set(null);
    this._loadedOrgId = null;
  }
}
