import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { EmpruntDto, EmpruntService } from '../../core/services/emprunt.service';
import { NotificationService } from '../../core/services/notification.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import { formatFcfa } from '../../core/utils/currency.util';
import { matchTextQuery } from '../../shared/util/filter.util';
import {
  buildEcheancesRecap,
  buildSuiviCard,
  compteursTab,
  empruntSoldeCeMois,
  filtrerParKpi,
  filtrerParTab,
  formatFcfaCompact,
  modalEcheanceRows,
  SuiviEmpruntCard,
  SuiviKpiFiltre,
  SuiviTab,
} from './suivi-emprunts.util';
import { DROIT_ACTION_IMPORTS } from '../../shared/imports/droit-action.imports';

@Component({
  selector: 'app-suivi-emprunts',
  standalone: true,
  imports: [RouterLink, ...DROIT_ACTION_IMPORTS],
  templateUrl: './suivi-emprunts.component.html',
  styleUrl: './suivi-emprunts.component.scss',
})
export class SuiviEmpruntsComponent implements OnInit, OnDestroy {
  readonly Math = Math;
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  private readonly empruntService = inject(EmpruntService);
  private readonly notify = inject(NotificationService);

  readonly formatFcfa = formatFcfa;
  readonly formatFcfaCompact = formatFcfaCompact;
  readonly modalEcheanceRows = modalEcheanceRows;

  readonly vueMembre = computed(() => this.auth.currentRole() === 'MEMBRE');

  readonly emprunts = signal<EmpruntDto[]>([]);
  readonly loading = signal(true);
  readonly tab = signal<SuiviTab>('tous');
  readonly kpiActif = signal<SuiviKpiFiltre | null>('tous');
  readonly recherche = signal('');
  readonly filtreStatut = signal<'tous' | 'retard' | 'encours' | 'solde'>('tous');
  readonly detailCarte = signal<SuiviEmpruntCard | null>(null);

  readonly cartes = computed(() => this.emprunts().map(buildSuiviCard));

  readonly kpi = computed(() => {
    const all = this.cartes();
    const actifs = all.filter((c) => !c.solde);
    return {
      totalActifs: actifs.length,
      retard: actifs.filter((c) => c.enRetard).length,
      encours: actifs.filter((c) => !c.enRetard).length,
      soldesMois: all.filter((c) => c.solde && empruntSoldeCeMois(c.emprunt)).length,
      encoursMontant: actifs.reduce((s, c) => s + c.montantRestant, 0),
    };
  });

  readonly compteurs = computed(() => compteursTab(this.cartes()));

  readonly cartesFiltrees = computed(() => {
    let list = filtrerParTab(this.cartes(), this.tab());
    list = filtrerParKpi(list, this.kpiActif());
    const fs = this.filtreStatut();
    if (fs === 'retard') list = list.filter((c) => c.enRetard);
    else if (fs === 'encours') list = list.filter((c) => !c.solde && !c.enRetard);
    else if (fs === 'solde') list = list.filter((c) => c.solde);
    const q = this.recherche();
    if (q.trim()) {
      list = list.filter((c) =>
        matchTextQuery(q, c.emprunt.membreNom, c.emprunt.codeMembre, c.reference)
      );
    }
    return list;
  });

  readonly recapEcheances = computed(() => buildEcheancesRecap(this.emprunts()));

  private orgId = 0;
  private sub = new Subscription();

  ngOnInit(): void {
    this.orgId = organisationCouranteId(this.route, this.auth) ?? 0;
    this.charger();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey) {
      if (event.key === '1') {
        event.preventDefault();
        this.tab.set('tous');
      } else if (event.key === '2') {
        event.preventDefault();
        this.tab.set('etale');
      } else if (event.key === '3') {
        event.preventDefault();
        this.tab.set('caisse');
      } else if (event.key === '4') {
        event.preventDefault();
        this.tab.set('sol');
      } else if (event.key === '5') {
        event.preventDefault();
        this.tab.set('soldes');
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.recherche.set('');
    }
  }

  charger(): void {
    if (this.orgId < 1) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    const req = this.vueMembre()
      ? this.empruntService.listerMesEmpruntsSuivi(this.orgId)
      : this.empruntService.listerSuivi(this.orgId);
    this.sub.add(
      req.subscribe({
        next: (list) => {
          this.emprunts.set(list ?? []);
          this.loading.set(false);
        },
        error: () => {
          this.emprunts.set([]);
          this.loading.set(false);
          this.notify.error('Impossible de charger les emprunts.');
        },
      })
    );
  }

  orgCourante(): number | null {
    return organisationCouranteId(this.route, this.auth);
  }

  setTab(t: SuiviTab): void {
    this.tab.set(t);
    this.kpiActif.set(null);
  }

  setKpi(k: SuiviKpiFiltre): void {
    this.kpiActif.set(this.kpiActif() === k ? null : k);
    if (k === 'soldes') this.tab.set('soldes');
    else if (k === 'retard' || k === 'encours') this.tab.set('tous');
  }

  kpiActive(k: SuiviKpiFiltre): boolean {
    return this.kpiActif() === k;
  }

  onRecherche(ev: Event): void {
    this.recherche.set((ev.target as HTMLInputElement).value);
  }

  onFiltreStatut(ev: Event): void {
    const v = (ev.target as HTMLSelectElement).value;
    this.filtreStatut.set(
      v === 'retard' || v === 'encours' || v === 'solde' ? v : 'tous'
    );
  }

  ouvrirDetail(c: SuiviEmpruntCard): void {
    this.detailCarte.set(c);
  }

  fermerDetail(): void {
    this.detailCarte.set(null);
  }

  onModalBackdrop(ev: MouseEvent): void {
    if ((ev.target as HTMLElement).classList.contains('modal-ov')) {
      this.fermerDetail();
    }
  }

  allerRemboursementParId(empruntId: number): void {
    const emp = this.emprunts().find((e) => e.id === empruntId);
    if (!emp) return;
    this.allerRemboursement(buildSuiviCard(emp));
  }

  allerRemboursement(c: SuiviEmpruntCard, ev?: Event): void {
    ev?.stopPropagation();
    const t =
      c.emprunt.typeEmprunt === 'SOLIDARITE'
        ? 'solidarite'
        : c.emprunt.typeEmprunt === 'CAISSE'
          ? 'caisse'
          : 'etale';
    void this.router.navigate(['/organisations', this.orgId, 'operations', 'remboursements'], {
      queryParams: { t, empruntId: c.emprunt.id },
    });
  }

  exporterExcel(): void {
    this.notify.show('📥 Export Excel — fonctionnalité à venir.');
  }

  /** Intérêts estimés = total à rembourser − capital initial (affichage modal). */
  interetsTotauxModal(c: SuiviEmpruntCard): number {
    const total = Number(c.emprunt.montantTotal) || 0;
    const capital = c.montantInitial;
    return Math.max(0, total - capital);
  }
}
