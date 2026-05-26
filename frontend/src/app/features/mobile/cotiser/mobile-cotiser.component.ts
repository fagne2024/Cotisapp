import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { MobileDataService } from '../shared/mobile-data.service';
import { MonCompteOperationService } from '../../../core/services/mon-compte-operation.service';
import { formatFcfa } from '../../../core/utils/currency.util';

type Onglet = 'cotiser' | 'rembourser';
type SousOnglet = 'hebdo' | 'mois';

@Component({
  selector: 'app-mobile-cotiser',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './mobile-cotiser.component.html',
  styleUrl: './mobile-cotiser.component.scss',
})
export class MobileCotiserComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly data = inject(MobileDataService);
  private readonly ops = inject(MonCompteOperationService);
  private readonly fb = inject(FormBuilder);
  protected readonly fmt = formatFcfa;

  protected readonly onglet = signal<Onglet>('cotiser');
  protected readonly sousOnglet = signal<SousOnglet>('hebdo');
  protected readonly loading = signal(false);
  protected readonly succes = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);

  protected readonly empruntsActifs = computed(() =>
    this.data.emprunts().filter((e) => e.statut === 'EN_COURS' || e.statut === 'RETARD'),
  );

  protected readonly formCotisation = this.fb.group({
    montant: [null as number | null, [Validators.required, Validators.min(1)]],
    modePaiement: ['MOBILE_MONEY', Validators.required],
    referencePaiement: [''],
  });

  protected readonly formRemboursement = this.fb.group({
    empruntId: [null as number | null, Validators.required],
    montant: [null as number | null, [Validators.required, Validators.min(1)]],
    modePaiement: ['MOBILE_MONEY', Validators.required],
    referencePaiement: [''],
  });

  ngOnInit(): void {
    const orgId = this.auth.currentOrgId();
    if (orgId) this.data.charger(orgId);
  }

  protected setOnglet(o: Onglet): void {
    this.onglet.set(o);
    this.succes.set(null);
    this.erreur.set(null);
  }

  protected setSousOnglet(s: SousOnglet): void {
    this.sousOnglet.set(s);
  }

  protected soumettreCotisation(): void {
    if (this.formCotisation.invalid) return;
    const orgId = this.auth.currentOrgId();
    if (!orgId) return;

    const val = this.formCotisation.value;
    this.loading.set(true);
    this.succes.set(null);
    this.erreur.set(null);

    const body = {
      montant: val.montant!,
      modePaiement: val.modePaiement ?? undefined,
      referencePaiement: val.referencePaiement || undefined,
    };

    const obs$ = this.sousOnglet() === 'hebdo'
      ? this.ops.validerCotisationHebdo(orgId, body as any)
      : this.ops.validerCotisationMois(orgId, body as any);

    obs$.subscribe({
      next: () => {
        this.loading.set(false);
        this.succes.set('Votre demande de cotisation a été soumise avec succès.');
        this.formCotisation.reset({ modePaiement: 'MOBILE_MONEY' });
        this.data.rafraichir(orgId);
      },
      error: (err) => {
        this.loading.set(false);
        this.erreur.set(err?.error?.message ?? 'Une erreur est survenue. Veuillez réessayer.');
      },
    });
  }

  protected soumettreRemboursement(): void {
    if (this.formRemboursement.invalid) return;
    const orgId = this.auth.currentOrgId();
    if (!orgId) return;

    const val = this.formRemboursement.value;
    this.loading.set(true);
    this.succes.set(null);
    this.erreur.set(null);

    this.ops.rembourser(orgId, val.empruntId!, {
      montant: val.montant!,
      modePaiement: val.modePaiement ?? undefined,
      referencePaiement: val.referencePaiement || undefined,
    } as any).subscribe({
      next: () => {
        this.loading.set(false);
        this.succes.set('Votre remboursement a été soumis avec succès.');
        this.formRemboursement.reset({ modePaiement: 'MOBILE_MONEY' });
        this.data.rafraichir(orgId);
      },
      error: (err) => {
        this.loading.set(false);
        this.erreur.set(err?.error?.message ?? 'Une erreur est survenue. Veuillez réessayer.');
      },
    });
  }

  protected typeLabel(type: string): string {
    const m: Record<string, string> = {
      ETALE: 'Étalé', SOLIDARITE: 'Solidarité', CAISSE: 'Caisse',
    };
    return m[type] ?? type;
  }
}
