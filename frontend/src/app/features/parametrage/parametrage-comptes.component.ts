import { Component, inject, OnInit, signal, HostListener } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import {
  FamilleCompte,
  ParametrageCompteDto,
  ParametrageCompteService,
  UpdateParametrageCompteItem,
} from '../../core/services/parametrage-compte.service';
import { NotificationService } from '../../core/services/notification.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import { formatFcfa } from '../../core/utils/currency.util';
import { ParametrageTabsComponent } from './parametrage-tabs.component';
import { DROIT_ACTION_IMPORTS } from '../../shared/imports/droit-action.imports';

const FAMILLES: FamilleCompte[] = [
  'CAISSE',
  'SOLIDARITE',
  'EPARGNE_HEBDO',
  'EPARGNE_MOIS',
  'PENALITE',
  'AMENDE',
];

const ICONS: Record<FamilleCompte, string> = {
  CAISSE: '💵',
  SOLIDARITE: '🤝',
  EPARGNE_HEBDO: '📅',
  EPARGNE_MOIS: '🗓',
  PENALITE: '⚠',
  AMENDE: '🚫',
};

@Component({
  selector: 'app-parametrage-comptes',
  standalone: true,
  imports: [ReactiveFormsModule, ParametrageTabsComponent, ...DROIT_ACTION_IMPORTS],
  templateUrl: './parametrage-comptes.component.html',
  styleUrl: './parametrage-comptes.component.scss',
})
export class ParametrageComptesComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly parametrageService = inject(ParametrageCompteService);
  private readonly notify = inject(NotificationService);

  readonly formatFcfa = formatFcfa;
  readonly familles = FAMILLES;
  readonly iconFor = (f: FamilleCompte) => ICONS[f];

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly items = signal<ParametrageCompteDto[]>([]);
  readonly form = this.fb.group({
    CAISSE: this.fb.group({
      libelle: ['', [Validators.required, Validators.maxLength(255)]],
      actif: [true],
    }),
    SOLIDARITE: this.fb.group({
      libelle: ['', [Validators.required, Validators.maxLength(255)]],
      actif: [true],
    }),
    EPARGNE_HEBDO: this.fb.group({
      libelle: ['', [Validators.required, Validators.maxLength(255)]],
      actif: [true],
    }),
    EPARGNE_MOIS: this.fb.group({
      libelle: ['', [Validators.required, Validators.maxLength(255)]],
      actif: [true],
    }),
    PENALITE: this.fb.group({
      libelle: ['', [Validators.required, Validators.maxLength(255)]],
      actif: [false],
    }),
    AMENDE: this.fb.group({
      libelle: ['', [Validators.required, Validators.maxLength(255)]],
      actif: [false],
    }),
  });

  private orgId = 0;

  ngOnInit(): void {
    this.orgId = organisationCouranteId(this.route, this.auth) ?? 0;
    if (!this.orgId) {
      this.loading.set(false);
      return;
    }
    this.parametrageService.lister(this.orgId).subscribe({
      next: (list) => this.applyList(list),
      error: () => {
        this.loading.set(false);
        this.showToast('Impossible de charger le paramétrage des comptes.');
      },
    });
  }

  itemFor(famille: FamilleCompte): ParametrageCompteDto | undefined {
    return this.items().find((i) => i.famille === famille);
  }

  group(famille: FamilleCompte) {
    return this.form.get(famille)!;
  }

  enregistrer(): void {
    if (this.form.invalid || !this.orgId) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const raw = this.form.getRawValue();
    this.parametrageService
      .mettreAJour(this.orgId, {
        comptes: {
          CAISSE: this.toUpdateItem(raw.CAISSE),
          SOLIDARITE: this.toUpdateItem(raw.SOLIDARITE),
          EPARGNE_HEBDO: this.toUpdateItem(raw.EPARGNE_HEBDO),
          EPARGNE_MOIS: this.toUpdateItem(raw.EPARGNE_MOIS),
          PENALITE: this.toUpdateItem(raw.PENALITE),
          AMENDE: this.toUpdateItem(raw.AMENDE),
        },
      })
      .subscribe({
        next: (list) => {
          this.applyList(list);
          this.saving.set(false);
          this.showToast('Paramétrage des comptes enregistré.');
        },
        error: () => {
          this.saving.set(false);
          this.showToast('Erreur lors de l’enregistrement.');
        },
      });
  }

  reinitialiser(): void {
    if (!this.orgId) return;
    this.loading.set(true);
    this.parametrageService.lister(this.orgId).subscribe({
      next: (list) => this.applyList(list),
      error: () => {
        this.loading.set(false);
        this.showToast('Rechargement impossible.');
      },
    });
  }

  private toUpdateItem(value: {
    libelle: string | null;
    actif: boolean | null;
  }): UpdateParametrageCompteItem {
    return {
      libelle: (value.libelle ?? '').trim(),
      actif: value.actif ?? true,
    };
  }

  private applyList(list: ParametrageCompteDto[]): void {
    this.items.set(list);
    for (const f of FAMILLES) {
      const row = list.find((x) => x.famille === f);
      if (!row) continue;
      this.group(f).patchValue({ libelle: row.libelle, actif: row.actif });
    }
    this.loading.set(false);
  }

  private showToast(msg: string): void {
    this.notify.show(msg);
  }
}
