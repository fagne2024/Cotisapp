import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';

import { Subscription } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { DROIT_ACTION_IMPORTS } from '../../shared/imports/droit-action.imports';

import { NotificationService } from '../../core/services/notification.service';

import {

  RapportBarChartItem,

  RapportCotisationMembre,

  RapportDepense,

  RapportEmpruntCard,

  RapportEmpruntSynthese,

  RapportHeroStat,

  RapportMembreFinancier,

  RapportParticipation,

  RapportPeriodeOption,

  RapportService,

} from '../../core/services/rapport.service';

import { organisationCouranteId } from '../../core/util/org-route.util';

import { formatFcfa } from '../../core/utils/currency.util';

import { FilterQueryNav, qpEnum, qpString } from '../../shared/util/filter-query.util';

import { matchTextQuery } from '../../shared/util/filter.util';

import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import { paginateSlice } from '../../shared/util/pagination.util';
import { RapportTabUi } from './rapport-demo.util';

@Component({
  selector: 'app-rapport-page',
  standalone: true,
  imports: [ListPaginationComponent, ...DROIT_ACTION_IMPORTS],
  templateUrl: './rapport-page.component.html',
  styleUrls: ['./rapport-page.component.scss', '../../shared/styles/pagination.scss'],
})

export class RapportPageComponent implements OnInit, OnDestroy {

  readonly Math = Math;
  private readonly route = inject(ActivatedRoute);

  private readonly router = inject(Router);

  private readonly auth = inject(AuthService);

  private readonly notify = inject(NotificationService);

  private readonly rapportApi = inject(RapportService);



  readonly formatFcfa = formatFcfa;

  readonly periodes = signal<RapportPeriodeOption[]>([]);

  readonly heroStats = signal<RapportHeroStat[]>([]);

  readonly chartBars = signal<RapportBarChartItem[]>([]);

  readonly participation = signal<RapportParticipation | null>(null);

  readonly cotisationsMembres = signal<RapportCotisationMembre[]>([]);

  readonly empruntsCards = signal<RapportEmpruntCard[]>([]);

  readonly empruntsSynthese = signal<RapportEmpruntSynthese | null>(null);

  readonly membresFinancier = signal<RapportMembreFinancier[]>([]);

  readonly depensesRapport = signal<RapportDepense[]>([]);

  readonly totalCotisationsMontant = signal(0);

  readonly totalDepensesMontant = signal(0);

  readonly nbMembresActifs = signal(0);

  readonly nbMembresBureau = signal(0);

  readonly chargement = signal(false);



  readonly tabUi = signal<RapportTabUi>('cotis');

  readonly periode = signal('');

  readonly periodeLabel = signal('');

  readonly filtrePoste = signal('tous');

  readonly filtreStatutCotis = signal<'tous' | 'complet' | 'partiel' | 'manque'>('tous');

  readonly filtreEmprStatut = signal<'tous' | 'retard' | 'sol' | 'cours'>('tous');

  readonly filtreSituation = signal<'tous' | 'ok' | 'retard'>('tous');

  readonly filtreDepCategorie = signal('tous');

  readonly filtreRecherche = signal('');

  readonly pageSize = 15;
  readonly pageCotis = signal(1);
  readonly pageEmpr = signal(1);
  readonly pageMembres = signal(1);
  readonly pageDepenses = signal(1);

  readonly orgNom = computed(() => this.auth.currentOrgNom() ?? 'Organisation');

  readonly dateRapport = computed(() => {

    const d = new Date();

    return new Intl.DateTimeFormat('fr-FR', {

      day: '2-digit',

      month: '2-digit',

      year: 'numeric',

    }).format(d);

  });



  readonly totalCotisations = computed(() => formatFcfa(this.totalCotisationsMontant()));

  readonly partPct = computed(() => this.participation()?.pctGlobal ?? 0);
  readonly partMembresLabel = computed(() => {
    const p = this.participation();
    return p ? `${p.membresAJour} / ${p.membresTotal} membres à jour` : '—';
  });
  readonly hebdoProgLabel = computed(() => {
    const p = this.participation();
    return p ? `${p.hebdoPayes} / ${p.hebdoTotal}` : '—';
  });
  readonly hebdoProgPct = computed(() => {
    const p = this.participation();
    return p && p.hebdoTotal > 0 ? (p.hebdoPayes * 100) / p.hebdoTotal : 0;
  });
  readonly moisProgLabel = computed(() => {
    const p = this.participation();
    return p ? `${p.moisPayes} / ${p.moisTotal}` : '—';
  });
  readonly moisProgPct = computed(() => {
    const p = this.participation();
    return p && p.moisTotal > 0 ? (p.moisPayes * 100) / p.moisTotal : 0;
  });
  readonly bureauProgLabel = computed(() => {
    const p = this.participation();
    return p ? `Bureau (${p.bureauTotal} membres)` : 'Bureau';
  });
  readonly bureauProgCount = computed(() => {
    const p = this.participation();
    return p ? `${p.bureauPayes} / ${p.bureauTotal}` : '—';
  });
  readonly bureauProgPct = computed(() => {
    const p = this.participation();
    return p && p.bureauTotal > 0 ? (p.bureauPayes * 100) / p.bureauTotal : 0;
  });
  readonly synth = computed(() => this.empruntsSynthese());



  readonly cotisationsFiltrees = computed(() => {

    const f = this.filtrePoste();

    const st = this.filtreStatutCotis();

    const q = this.filtreRecherche();

    let rows = this.cotisationsMembres();

    if (f === 'bureau') {

      rows = rows.filter((r) => !r.posteBadgeClass.includes('gray'));

    } else if (f === 'simple') {

      rows = rows.filter((r) => r.posteBadgeClass.includes('gray'));

    }

    if (st !== 'tous') {

      rows = rows.filter((r) => r.statut === st);

    }

    return rows.filter((r) => matchTextQuery(q, r.nom, r.code, r.posteLabel));
  });

  readonly cotisationsFiltreesPaged = computed(() =>
    paginateSlice(this.cotisationsFiltrees(), this.pageCotis(), this.pageSize)
  );

  readonly empruntsCardsFiltres = computed(() => {

    const f = this.filtreEmprStatut();

    const q = this.filtreRecherche();

    return this.empruntsCards().filter((e) => {

      if (f === 'retard' && e.borderClass !== 'retard') return false;

      if (f === 'sol' && e.borderClass !== 'sol') return false;

      if (f === 'cours' && e.borderClass !== 'cours') return false;

      return matchTextQuery(q, e.nom, e.detail, e.badge);

    });
  });

  readonly empruntsCardsFiltresPaged = computed(() =>
    paginateSlice(this.empruntsCardsFiltres(), this.pageEmpr(), this.pageSize)
  );

  readonly membresFinancierFiltres = computed(() => {

    const f = this.filtreSituation();

    const q = this.filtreRecherche();

    return this.membresFinancier().filter((m) => {

      if (f === 'retard' && !m.situationClass.includes('red')) return false;

      if (f === 'ok' && m.situationClass.includes('red')) return false;

      return matchTextQuery(q, m.nom, m.code, m.situation);

    });
  });

  readonly membresFinancierFiltresPaged = computed(() =>
    paginateSlice(this.membresFinancierFiltres(), this.pageMembres(), this.pageSize)
  );

  readonly depensesRapportFiltres = computed(() => {

    const cat = this.filtreDepCategorie();

    const q = this.filtreRecherche();

    return this.depensesRapport().filter((d) => {

      if (cat !== 'tous' && d.categorieId !== cat && !d.categorie.toLowerCase().includes(cat)) {

        return false;

      }

      return matchTextQuery(q, d.categorie, d.beneficiaire, d.description, d.saisiPar);

    });
  });

  readonly depensesRapportFiltresPaged = computed(() =>
    paginateSlice(this.depensesRapportFiltres(), this.pageDepenses(), this.pageSize)
  );

  readonly totalDepenses = computed(() =>

    this.depensesRapportFiltres().reduce((s, d) => s + d.montant, 0)

  );



  private orgId = 0;

  private sub = new Subscription();

  private readonly queryNav = new FilterQueryNav();

  private readonly queryDefaults = {

    tab: 'cotis',

    periode: '',

    poste: 'tous',

    scotis: 'tous',

    sempr: 'tous',

    sit: 'tous',

    cat: 'tous',

    q: '',

  };



  ngOnInit(): void {

    this.orgId = organisationCouranteId(this.route, this.auth) ?? 0;



    this.sub.add(

      this.route.queryParamMap.subscribe((pm) => {

        this.queryNav.runSync(() => {

          const t = pm.get('tab');

          const tab: RapportTabUi =

            t === 'empr' || t === 'membres' || t === 'depenses' ? t : 'cotis';

          this.tabUi.set(tab);

          const periode = qpString(pm, 'periode', 16);

          if (periode) {

            this.periode.set(periode);

          }

          this.filtrePoste.set(qpString(pm, 'poste', 16) || 'tous');

          this.filtreStatutCotis.set(

            qpEnum(pm, 'scotis', ['tous', 'complet', 'partiel', 'manque'] as const, 'tous')

          );

          this.filtreEmprStatut.set(

            qpEnum(pm, 'sempr', ['tous', 'retard', 'sol', 'cours'] as const, 'tous')

          );

          this.filtreSituation.set(qpEnum(pm, 'sit', ['tous', 'ok', 'retard'] as const, 'tous'));

          const cat = qpString(pm, 'cat', 32);

          this.filtreDepCategorie.set(cat || 'tous');

          this.filtreRecherche.set(qpString(pm, 'q'));

        });

        if (this.orgId > 0) {

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
        this.setTab('cotis');
      } else if (event.key === '2') {
        event.preventDefault();
        this.setTab('empr');
      } else if (event.key === '3') {
        event.preventDefault();
        this.setTab('membres');
      } else if (event.key === '4') {
        event.preventDefault();
        this.setTab('depenses');
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.filtreRecherche.set('');
    }
  }



  private chargerRapport(): void {

    this.chargement.set(true);

    const p = this.periode() || undefined;

    this.rapportApi.charger(this.orgId, p).subscribe({

      next: (d) => {

        if (d.periodesDisponibles?.length) {

          this.periodes.set(d.periodesDisponibles);

        }

        if (!this.periode() && d.periode) {

          this.periode.set(d.periode);

        }

        this.periodeLabel.set(d.periodeLabel ?? '');

        this.heroStats.set(d.heroStats ?? []);

        this.chartBars.set(d.cotisationsParSemaine ?? []);

        this.participation.set(d.participation ?? null);

        this.cotisationsMembres.set(d.cotisationsMembres ?? []);

        this.empruntsCards.set(d.emprunts ?? []);

        this.empruntsSynthese.set(d.empruntsSynthese ?? null);

        this.membresFinancier.set(d.membresFinancier ?? []);

        this.depensesRapport.set(d.depenses ?? []);

        this.totalCotisationsMontant.set(Number(d.totalCotisations) || 0);

        this.totalDepensesMontant.set(Number(d.totalDepenses) || 0);

        this.nbMembresActifs.set(d.nbMembresActifs ?? 0);

        this.nbMembresBureau.set(d.nbMembresBureau ?? 0);

        this.reinitialiserToutesLesPages();
        this.chargement.set(false);

      },

      error: (err) => {

        this.chargement.set(false);

        this.notify.show(err?.error?.message ?? 'Impossible de charger le rapport.');

      },

    });

  }



  private pushFiltersToUrl(debounce = false): void {

    this.queryNav.push(

      this.router,

      this.route,

      {

        tab: this.tabUi(),

        periode: this.periode(),

        poste: this.filtrePoste(),

        scotis: this.filtreStatutCotis(),

        sempr: this.filtreEmprStatut(),

        sit: this.filtreSituation(),

        cat: this.filtreDepCategorie(),

        q: this.filtreRecherche(),

      },

      this.queryDefaults,

      debounce ? 400 : 0

    );

  }



  setTab(tab: RapportTabUi): void {

    void this.router.navigate([], {

      relativeTo: this.route,

      queryParams: { tab },

      queryParamsHandling: 'merge',

      replaceUrl: true,

    });

  }



  onPeriodeChange(ev: Event): void {
    const v = (ev.target as HTMLSelectElement).value;
    this.periode.set(v);
    this.reinitialiserToutesLesPages();
    this.pushFiltersToUrl();
  }



  onFiltrePosteChange(ev: Event): void {
    this.filtrePoste.set((ev.target as HTMLSelectElement).value);
    this.pageCotis.set(1);
    this.pushFiltersToUrl();
  }



  onFiltreStatutCotisChange(ev: Event): void {

    const v = (ev.target as HTMLSelectElement).value;

    this.filtreStatutCotis.set(

      v === 'complet' || v === 'partiel' || v === 'manque' ? v : 'tous'

    );

    this.pageCotis.set(1);
    this.pushFiltersToUrl();
  }

  onFiltreEmprStatutChange(ev: Event): void {

    const v = (ev.target as HTMLSelectElement).value;

    this.filtreEmprStatut.set(v === 'retard' || v === 'sol' || v === 'cours' ? v : 'tous');
    this.pageEmpr.set(1);
    this.pushFiltersToUrl();
  }

  onFiltreSituationChange(ev: Event): void {

    const v = (ev.target as HTMLSelectElement).value;

    this.filtreSituation.set(v === 'ok' || v === 'retard' ? v : 'tous');
    this.pageMembres.set(1);
    this.pushFiltersToUrl();
  }

  onFiltreDepCategorieChange(ev: Event): void {
    this.filtreDepCategorie.set((ev.target as HTMLSelectElement).value);
    this.pageDepenses.set(1);
    this.pushFiltersToUrl();
  }

  onFiltreRecherche(ev: Event): void {
    this.filtreRecherche.set((ev.target as HTMLInputElement).value);
    this.reinitialiserPageOngletActif();
    this.pushFiltersToUrl(true);
  }

  onPageCotisChange(p: number): void {
    this.pageCotis.set(p);
  }

  onPageEmprChange(p: number): void {
    this.pageEmpr.set(p);
  }

  onPageMembresChange(p: number): void {
    this.pageMembres.set(p);
  }

  onPageDepensesChange(p: number): void {
    this.pageDepenses.set(p);
  }

  private reinitialiserToutesLesPages(): void {
    this.pageCotis.set(1);
    this.pageEmpr.set(1);
    this.pageMembres.set(1);
    this.pageDepenses.set(1);
  }

  private reinitialiserPageOngletActif(): void {
    switch (this.tabUi()) {
      case 'cotis':
        this.pageCotis.set(1);
        break;
      case 'empr':
        this.pageEmpr.set(1);
        break;
      case 'membres':
        this.pageMembres.set(1);
        break;
      case 'depenses':
        this.pageDepenses.set(1);
        break;
    }
  }



  statutBadgeClass(row: RapportCotisationMembre): string {

    if (row.statut === 'complet') return 'b-green';

    if (row.statut === 'manque') return 'b-or';

    return 'b-gray';

  }



  exportToast(format: string): void {

    this.notify.info(`Export ${format} — fonctionnalité à brancher`);

  }



  imprimer(): void {

    window.print();

  }



  formatMontant(v: number): string {

    return formatFcfa(v);

  }

}


