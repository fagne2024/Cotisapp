import { NgClass } from '@angular/common';
import { Component, computed, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { FilterQueryNav, qpEnum, qpString } from '../../shared/util/filter-query.util';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { AuthService } from '../../core/services/auth.service';
import { StatutSuivi, SuiviMensuelDto, SuiviMensuelService } from '../../core/services/suivi-mensuel.service';
import { formatFcfa } from '../../core/utils/currency.util';
import { matchTextQuery } from '../../shared/util/filter.util';
import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import {
  clampPage,
  paginateSlice,
  paginationTotalPages,
} from '../../shared/util/pagination.util';

@Component({
  selector: 'app-suivi-mensuel-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatTableModule,
    NgClass,
    ListPaginationComponent,
  ],
  templateUrl: './suivi-mensuel-list.component.html',
  styleUrls: ['./suivi-mensuel-list.component.scss', '../../shared/styles/pagination.scss'],
})
export class SuiviMensuelListComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly queryNav = new FilterQueryNav();
  private readonly queryDefaults = { mois: '2026-05', statut: 'tous', q: '' };
  private sub = new Subscription();
  private readonly suiviService = inject(SuiviMensuelService);

  readonly formatFcfa = formatFcfa;
  readonly moisCtrl = new FormControl('2026-05', { nonNullable: true });
  readonly suivis = signal<SuiviMensuelDto[]>([]);
  readonly loading = signal(false);
  readonly message = signal<string | null>(null);

  readonly moisOptions = [
    { value: '2026-05', label: 'Mai 2026' },
    { value: '2026-04', label: 'Avril 2026' },
    { value: '2026-03', label: 'Mars 2026' },
  ];

  readonly displayedColumns = ['membre', 'montantDu', 'montantPaye', 'statut'];

  readonly filtreStatut = signal<'tous' | StatutSuivi>('tous');
  readonly filtreRecherche = signal('');

  readonly suivisFiltres = computed(() => {
    const st = this.filtreStatut();
    const q = this.filtreRecherche();
    return this.suivis().filter((s) => {
      if (st !== 'tous' && s.statut !== st) return false;
      return matchTextQuery(q, s.membreNom, s.codeMembre);
    });
  });

  readonly page = signal(1);
  readonly pageSize = 15;
  readonly suivisPaged = computed(() =>
    paginateSlice(this.suivisFiltres(), this.page(), this.pageSize)
  );

  private orgId = 0;

  ngOnInit(): void {
    this.orgId =
      Number(this.route.parent?.snapshot.paramMap.get('orgId')) ||
      this.auth.currentOrgId() ||
      1;

    this.sub.add(
      this.route.queryParamMap.subscribe((pm) => {
        this.queryNav.runSync(() => {
          const mois = qpString(pm, 'mois', 16);
          if (mois && this.moisOptions.some((m) => m.value === mois)) {
            this.moisCtrl.setValue(mois, { emitEvent: false });
          }
          this.filtreStatut.set(
            qpEnum(pm, 'statut', ['tous', 'PAYE', 'PARTIEL', 'NON_PAYE'] as const, 'tous')
          );
          this.filtreRecherche.set(qpString(pm, 'q'));
        });
      })
    );

    this.sub.add(
      this.moisCtrl.valueChanges.subscribe(() => {
        this.pushFiltersToUrl();
        this.charger();
      })
    );

    this.charger();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.queryNav.destroy();
  }

  private pushFiltersToUrl(debounce = false): void {
    this.queryNav.push(
      this.router,
      this.route,
      {
        mois: this.moisCtrl.value,
        statut: this.filtreStatut(),
        q: this.filtreRecherche(),
      },
      this.queryDefaults,
      debounce ? 400 : 0
    );
  }

  charger(): void {
    this.loading.set(true);
    this.message.set(null);
    this.suiviService.lister(this.orgId, this.moisCtrl.value).subscribe({
      next: (data) => {
        this.suivis.set(data);
        this.page.set(1);
        this.loading.set(false);
      },
      error: () => {
        this.suivis.set([]);
        this.loading.set(false);
      },
    });
  }

  generer(): void {
    this.suiviService.generer(this.orgId, this.moisCtrl.value).subscribe({
      next: (res) => {
        this.message.set(`${res.cree} fiche(s) créée(s) pour ${res.mois}.`);
        this.charger();
      },
      error: () => this.message.set('Erreur lors de la génération.'),
    });
  }

  statutClass(statut: StatutSuivi): string {
    switch (statut) {
      case 'PAYE':
        return 'badge-payé';
      case 'PARTIEL':
        return 'badge-partiel';
      default:
        return 'badge-non-paye';
    }
  }

  onFiltreStatut(ev: Event): void {
    const v = (ev.target as HTMLSelectElement).value;
    this.filtreStatut.set(
      v === 'PAYE' || v === 'PARTIEL' || v === 'NON_PAYE' ? (v as StatutSuivi) : 'tous'
    );
    this.page.set(1);
    this.pushFiltersToUrl();
  }

  onFiltreRecherche(ev: Event): void {
    this.filtreRecherche.set((ev.target as HTMLInputElement).value);
    this.page.set(1);
    this.pushFiltersToUrl(true);
  }

  goPage(p: number): void {
    this.page.set(clampPage(p, paginationTotalPages(this.suivisFiltres().length, this.pageSize)));
  }

  statutLabel(statut: StatutSuivi): string {
    switch (statut) {
      case 'PAYE':
        return 'Payé';
      case 'PARTIEL':
        return 'Partiel';
      default:
        return 'Non payé';
    }
  }
}
