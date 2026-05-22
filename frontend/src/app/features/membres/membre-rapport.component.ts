import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import {
  RapportMembreDto,
  RapportService,
} from '../../core/services/rapport.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import { formatFcfa } from '../../core/utils/currency.util';
import { FilterQueryNav, qpString } from '../../shared/util/filter-query.util';
import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import { paginateSlice } from '../../shared/util/pagination.util';

type RapportMembreTab = 'cotis' | 'empr' | 'ops';

@Component({
  selector: 'app-membre-rapport',
  standalone: true,
  imports: [RouterLink, ListPaginationComponent],
  templateUrl: './membre-rapport.component.html',
  styleUrls: [
    './membre-rapport.component.scss',
    '../rapports/rapport-page.component.scss',
    '../../shared/styles/pagination.scss',
  ],
})
export class MembreRapportComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotificationService);
  private readonly rapportApi = inject(RapportService);

  readonly formatFcfa = formatFcfa;
  readonly tabUi = signal<RapportMembreTab>('cotis');
  readonly periode = signal('');
  readonly periodes = signal<{ value: string; label: string }[]>([]);
  readonly rapport = signal<RapportMembreDto | null>(null);
  readonly chargement = signal(false);

  readonly vueMonCompte = computed(() => this.router.url.includes('/mon-compte'));
  readonly orgNom = computed(() => this.auth.currentOrgNom() ?? 'Organisation');
  readonly periodeLabel = computed(() => this.rapport()?.periodeLabel ?? '');
  readonly dateRapport = computed(() =>
    new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(
      new Date()
    )
  );

  readonly heroStats = computed(() => this.rapport()?.heroStats ?? []);
  readonly chartBars = computed(() => this.rapport()?.cotisationsParSemaine ?? []);
  readonly emprunts = computed(() => this.rapport()?.emprunts ?? []);
  readonly operations = computed(() => this.rapport()?.operations ?? []);
  readonly comptes = computed(() => this.rapport()?.comptes ?? []);

  readonly pageOps = signal(1);
  readonly pageSizeOps = 15;
  readonly operationsPaged = computed(() =>
    paginateSlice(this.operations(), this.pageOps(), this.pageSizeOps)
  );

  readonly statutBadgeClass = computed(() => {
    const s = this.rapport()?.statutCotisation;
    if (s === 'complet') return 'b-green';
    if (s === 'manque') return 'b-or';
    return 'b-gray';
  });

  private orgId = 0;
  private membreId = 0;
  private sub = new Subscription();
  private readonly queryNav = new FilterQueryNav();
  private readonly queryDefaults = { tab: 'cotis', periode: '' };

  ngOnInit(): void {
    this.orgId = organisationCouranteId(this.route, this.auth) ?? 0;
    const paramId = this.route.snapshot.paramMap.get('membreId');
    if (paramId) {
      this.membreId = Number(paramId);
    } else if (this.vueMonCompte()) {
      this.membreId = this.auth.currentMembreId() ?? 0;
    }

    this.sub.add(
      this.route.queryParamMap.subscribe((pm) => {
        this.queryNav.runSync(() => {
          const t = pm.get('tab');
          this.tabUi.set(t === 'empr' || t === 'ops' ? t : 'cotis');
          const periode = qpString(pm, 'periode', 16);
          if (periode) {
            this.periode.set(periode);
          }
        });
        if (this.orgId > 0 && this.membreId > 0) {
          this.chargerRapport();
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.queryNav.destroy();
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey) {
      if (event.key === '1') {
        event.preventDefault();
        this.tabUi.set('cotis');
      } else if (event.key === '2') {
        event.preventDefault();
        this.tabUi.set('empr');
      } else if (event.key === '3') {
        event.preventDefault();
        this.tabUi.set('ops');
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.tabUi.set('cotis');
    }
  }

  ficheLink(): (string | number)[] {
    if (this.vueMonCompte()) {
      return ['/organisations', this.orgId, 'mon-compte'];
    }
    return ['/organisations', this.orgId, 'membres', this.membreId];
  }

  retourListeLink(): (string | number)[] | null {
    if (this.vueMonCompte()) {
      return null;
    }
    return ['/organisations', this.orgId, 'membres'];
  }

  private chargerRapport(): void {
    this.chargement.set(true);
    this.rapportApi.chargerMembre(this.orgId, this.membreId, this.periode() || undefined).subscribe({
      next: (d) => {
        this.rapport.set(d);
        this.pageOps.set(1);
        if (d.periodesDisponibles?.length) {
          this.periodes.set(d.periodesDisponibles);
        }
        if (!this.periode() && d.periode) {
          this.periode.set(d.periode);
        }
        this.chargement.set(false);
      },
      error: (err) => {
        this.chargement.set(false);
        this.notify.show(err?.error?.message ?? 'Impossible de charger le rapport membre.');
      },
    });
  }

  setTab(tab: RapportMembreTab): void {
    if (tab === 'ops') {
      this.pageOps.set(1);
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  onPeriodeChange(ev: Event): void {
    this.periode.set((ev.target as HTMLSelectElement).value);
    this.pageOps.set(1);
    this.queryNav.push(
      this.router,
      this.route,
      { tab: this.tabUi(), periode: this.periode() },
      this.queryDefaults,
      0
    );
  }

  imprimer(): void {
    window.print();
  }
}
