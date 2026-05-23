import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';

import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ActivatedRoute, Router } from '@angular/router';

import { forkJoin, from, of, Subscription } from 'rxjs';

import {

  catchError,

  concatMap,

  debounceTime,

  distinctUntilChanged,

  finalize,

  map,

  switchMap,

  toArray,

} from 'rxjs/operators';

import { AuthService } from '../../../core/services/auth.service';

import { MembreDto, MembreService, MembreSoldesDto } from '../../../core/services/membre.service';

import { NotificationService } from '../../../core/services/notification.service';

import {

  AppliquerSanctionRequest,

  PenaliteAmendeHistoriqueLigneDto,

  PenaliteAmendeService,

  PenaliteAmendeTopMembreDto,

} from '../../../core/services/penalite-amende.service';

import { organisationCouranteId } from '../../../core/util/org-route.util';

import { formatFcfa } from '../../../core/utils/currency.util';

import { postePourCodeMembre } from '../../membres/membres-poste.util';

import { FilterQueryNav, qpEnum, qpString } from '../../../shared/util/filter-query.util';

import { matchTextQuery } from '../../../shared/util/filter.util';

import {

  filtrerMembresParNumeroCode,

  suffixeCodeNumerique,

} from '../../../shared/util/membre-code-lookup.util';

import { ListPaginationComponent } from '../../../shared/components/list-pagination/list-pagination.component';
import { DROIT_ACTION_IMPORTS } from '../../../shared/imports/droit-action.imports';

import {

  paginateSlice,

  paginationTotalPages,

} from '../../../shared/util/pagination.util';

import {

  HistoriqueSanction,

  MOTIFS_AMENDE,

  MOTIFS_PENALITE,

  MotifOption,

  SanctionTypeUi,

  TopPenalise,

} from './penalite-amende-demo.util';



@Component({

  selector: 'app-penalite-amende',

  standalone: true,

  imports: [ReactiveFormsModule, ListPaginationComponent, ...DROIT_ACTION_IMPORTS],

  templateUrl: './penalite-amende.component.html',

  styleUrls: [

    './penalite-amende.component.scss',

    '../../../shared/styles/pagination.scss',

    '../../../shared/styles/membre-selection-mode.scss',

    '../../../shared/styles/membre-search-row.scss',

  ],

})

export class PenaliteAmendeComponent implements OnInit, OnDestroy {

  private readonly fb = inject(FormBuilder);

  private readonly route = inject(ActivatedRoute);

  private readonly router = inject(Router);

  private readonly auth = inject(AuthService);

  private readonly membreService = inject(MembreService);

  private readonly penaliteAmendeApi = inject(PenaliteAmendeService);

  private readonly notify = inject(NotificationService);



  readonly formatFcfa = formatFcfa;

  readonly postePourCodeMembre = postePourCodeMembre;

  readonly typeUi = signal<SanctionTypeUi>('pen');

  readonly soldesParMembre = signal<Map<number, MembreSoldesDto>>(new Map());

  readonly historique = signal<HistoriqueSanction[]>([]);

  readonly topPenalises = signal<TopPenalise[]>([]);

  readonly soldeCaisse = signal(0);

  readonly statsMois = signal({

    moisLabel: '',

    penalites: 0,

    amendes: 0,

    totalEncaisse: 0,

  });

  readonly loadingPanneau = signal(false);

  readonly loading = signal(false);

  readonly membreListOpen = signal(false);

  readonly motifId = signal('absence');



  readonly membresRecherche = signal<MembreDto[]>([]);

  readonly modeSaisieMembre = signal<'unitaire' | 'bulk'>('unitaire');

  readonly membresBulk = signal<MembreDto[]>([]);

  readonly membresBulkCatalogue = signal<MembreDto[]>([]);

  readonly membresBulkCatalogueLoading = signal(false);

  readonly bulkFiltre = signal('');

  readonly bulkPage = signal(1);

  readonly bulkPageSize = 10;

  readonly rechercheMembreLoading = signal(false);



  readonly motifsActifs = computed(() =>

    this.typeUi() === 'pen' ? MOTIFS_PENALITE : MOTIFS_AMENDE

  );



  readonly motifSelectionne = computed(() => {

    const id = this.motifId();

    return this.motifsActifs().find((m) => m.id === id) ?? this.motifsActifs()[0];

  });



  readonly form = this.fb.nonNullable.group({

    membreId: [null as number | null, Validators.required],

    membreSearch: [''],

    membreCodeNumero: [''],

    montant: [2000, [Validators.required, Validators.min(100)]],

    dateApplication: [this.todayIso(), Validators.required],

    observation: [''],

  });



  readonly filteredMembres = computed(() => this.membresRecherche());



  readonly selectedMembre = computed(() => {

    if (this.modeSaisieMembre() === 'bulk') {

      return this.membresBulk()[0] ?? null;

    }

    const id = this.form.controls.membreId.value;

    if (id == null) return null;

    return (

      this.membresRecherche().find((m) => m.id === id) ??

      this.membresBulk().find((m) => m.id === id) ??

      null

    );

  });



  readonly simMontant = computed(() => this.form.controls.montant.value ?? 0);



  readonly bulkNbMembres = computed(() => this.membresBulk().length);



  readonly bulkMontantTotal = computed(() => {

    const n = this.bulkNbMembres();

    const m = this.simMontant();

    return n > 0 && m > 0 ? n * m : 0;

  });



  readonly simSanctionApres = computed(() => {

    const m = this.selectedMembre();

    if (!m) return 0;

    const s = this.soldesParMembre().get(m.id);

    const base = this.typeUi() === 'pen' ? Number(s?.penalite ?? 0) : Number(s?.amende ?? 0);

    return base + this.simMontant();

  });



  readonly simCaisseApres = computed(() => {

    if (this.modeSaisieMembre() === 'bulk') {

      return this.soldeCaisse() + this.bulkMontantTotal();

    }

    return this.soldeCaisse() + this.simMontant();

  });



  readonly membresBulkFiltres = computed(() => {

    const q = this.bulkFiltre().trim();

    const list = this.membresBulkCatalogue();

    if (!q) return list;

    return list.filter((m) =>

      matchTextQuery(q, m.codeMembre, m.nomComplet, m.nom, m.prenom, m.telephone ?? '')

    );

  });



  readonly bulkCatalogueTotal = computed(() => this.membresBulkFiltres().length);



  readonly bulkTotalPages = computed(() =>

    paginationTotalPages(this.bulkCatalogueTotal(), this.bulkPageSize)

  );



  readonly membresBulkPage = computed(() =>

    paginateSlice(this.membresBulkFiltres(), this.bulkPage(), this.bulkPageSize)

  );



  readonly bulkPageToutSelectionnee = computed(() => {

    const page = this.membresBulkPage();

    return page.length > 0 && page.every((m) => this.isMembreBulkSelected(m.id));

  });



  readonly filtreHistType = signal<'tous' | SanctionTypeUi>('tous');

  readonly filtreHistRecherche = signal('');



  readonly historiqueAffiche = computed(() => {

    const type = this.filtreHistType();

    const q = this.filtreHistRecherche();

    return this.historique().filter((h) => {

      if (type !== 'tous' && h.type !== type) return false;

      return matchTextQuery(q, h.membreNom, h.codeMembre, h.motif);

    });

  });



  private orgId = 0;

  private sub = new Subscription();

  private prevType: SanctionTypeUi | null = null;

  private readonly queryNav = new FilterQueryNav();

  private readonly queryDefaults = { hist: 'tous', q: '' };



  ngOnInit(): void {

    this.orgId = organisationCouranteId(this.route, this.auth) ?? 0;



    this.sub.add(

      this.route.queryParamMap.subscribe((pm) => {

        const t: SanctionTypeUi = pm.get('t') === 'am' ? 'am' : 'pen';

        const changed = this.prevType !== null && this.prevType !== t;

        this.prevType = t;

        this.typeUi.set(t);

        if (changed) {

          this.motifId.set(t === 'pen' ? 'absence' : 'reglement');

          this.form.patchValue({ montant: t === 'pen' ? 2000 : 5000 });

        }

        this.queryNav.runSync(() => {

          this.filtreHistType.set(

            qpEnum(pm, 'hist', ['tous', 'pen', 'am'] as const, 'tous')

          );

          this.filtreHistRecherche.set(qpString(pm, 'q'));

        });

      })

    );



    this.sub.add(

      this.form.controls.membreSearch.valueChanges

        .pipe(

          debounceTime(280),

          distinctUntilChanged(),

          switchMap((q) => {

            const term = (q ?? '').trim();

            if (term.length < 1 || this.orgId < 1) {

              this.membresRecherche.set([]);

              return of([] as MembreDto[]);

            }

            this.rechercheMembreLoading.set(true);

            return this.membreService.rechercher(this.orgId, term);

          })

        )

        .subscribe({

          next: (list) => {

            this.membresRecherche.set(list);

            this.rechercheMembreLoading.set(false);

          },

          error: () => {

            this.membresRecherche.set([]);

            this.rechercheMembreLoading.set(false);

          },

        })

    );



    this.sub.add(

      this.form.controls.membreCodeNumero.valueChanges

        .pipe(debounceTime(350), distinctUntilChanged())

        .subscribe((v) => {

          const n = (v ?? '').trim();

          if (n.length >= 1) {

            this.rechercherMembreParCodeNumero(false);

          }

        })

    );



    this.chargerDonnees();

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

        this.setType('pen');

      } else if (event.key === '2') {

        event.preventDefault();

        this.setType('am');

      } else if (event.key === 'e' || event.key === 'E') {

        event.preventDefault();

        this.valider();

      } else if (event.key === 'r' || event.key === 'R') {

        event.preventDefault();

        this.annuler();

      }

    } else if (event.key === 'Escape') {

      event.preventDefault();

      this.annuler();

    }

  }



  private chargerDonnees(): void {

    if (!this.orgId) return;

    this.loadingPanneau.set(true);

    this.sub.add(

      forkJoin({

        soldes: this.membreService.listerSoldesComptes(this.orgId),

        panneau: this.penaliteAmendeApi.chargerPanneau(this.orgId),

      }).subscribe({

        next: ({ soldes, panneau }) => {

          const map = new Map<number, MembreSoldesDto>();

          for (const s of soldes) {

            map.set(s.membreId, s);

          }

          this.soldesParMembre.set(map);

          this.soldeCaisse.set(Number(panneau.soldeCaisse) || 0);

          this.statsMois.set({

            moisLabel: panneau.statsMois.moisLabel,

            penalites: panneau.statsMois.penalites,

            amendes: panneau.statsMois.amendes,

            totalEncaisse: Number(panneau.statsMois.totalEncaisse) || 0,

          });

          this.historique.set(panneau.historique.map((h) => this.mapHistorique(h)));

          this.topPenalises.set(panneau.topPenalises.map((t, i) => this.mapTop(t, i === 0)));

          this.loadingPanneau.set(false);

        },

        error: (err) => {

          this.loadingPanneau.set(false);

          this.notify.info(err?.error?.message ?? 'Impossible de charger les pénalités / amendes');

        },

      })

    );

  }



  private mapHistorique(h: PenaliteAmendeHistoriqueLigneDto): HistoriqueSanction {

    return {

      membreNom: h.membreNom,

      codeMembre: h.codeMembre,

      type: h.type,

      motif: h.motif,

      montant: Number(h.montant),

      dateLabel: h.dateLabel,

    };

  }



  private mapTop(t: PenaliteAmendeTopMembreDto, highlight: boolean): TopPenalise {

    return {

      nom: t.nom,

      codeMembre: t.codeMembre,

      initials: this.initials(t.nom),

      avColor: this.avatarColor(t.codeMembre),

      detail: t.detail,

      total: Number(t.total),

      highlight,

    };

  }



  private pushFiltersToUrl(debounce = false): void {

    this.queryNav.push(

      this.router,

      this.route,

      { hist: this.filtreHistType(), q: this.filtreHistRecherche() },

      this.queryDefaults,

      debounce ? 400 : 0

    );

  }



  onFiltreHistType(ev: Event): void {

    const v = (ev.target as HTMLSelectElement).value;

    this.filtreHistType.set(v === 'pen' || v === 'am' ? v : 'tous');

    this.pushFiltersToUrl();

  }



  onFiltreHistRecherche(ev: Event): void {

    this.filtreHistRecherche.set((ev.target as HTMLInputElement).value);

    this.pushFiltersToUrl(true);

  }



  setType(t: SanctionTypeUi): void {

    void this.router.navigate([], {

      relativeTo: this.route,

      queryParams: { t },

      queryParamsHandling: 'merge',

      replaceUrl: true,

    });

  }



  setModeSaisieMembre(mode: 'unitaire' | 'bulk'): void {

    if (this.modeSaisieMembre() === mode) return;

    this.modeSaisieMembre.set(mode);

    const ctl = this.form.controls.membreId;

    if (mode === 'bulk') {

      ctl.clearValidators();

      ctl.setValue(null, { emitEvent: false });

      this.form.patchValue({ membreSearch: '', membreCodeNumero: '' }, { emitEvent: false });

      this.membreListOpen.set(false);

      this.bulkFiltre.set('');

      this.bulkPage.set(1);

      this.chargerMembresBulkCatalogue();

    } else {

      ctl.setValidators(Validators.required);

      this.membresBulk.set([]);

      this.bulkFiltre.set('');

      this.bulkPage.set(1);

    }

    ctl.updateValueAndValidity({ emitEvent: false });

  }



  chargerMembresBulkCatalogue(): void {

    if (this.orgId < 1) return;

    this.membresBulkCatalogueLoading.set(true);

    this.membreService.lister(this.orgId, true).subscribe({

      next: (list) => {

        const sorted = [...list].sort((a, b) =>

          a.codeMembre.localeCompare(b.codeMembre, undefined, { numeric: true })

        );

        this.membresBulkCatalogue.set(sorted);

        this.membresBulkCatalogueLoading.set(false);

        const maxPage = paginationTotalPages(sorted.length, this.bulkPageSize);

        if (this.bulkPage() > maxPage) {

          this.bulkPage.set(maxPage);

        }

      },

      error: () => {

        this.membresBulkCatalogue.set([]);

        this.membresBulkCatalogueLoading.set(false);

        this.notify.show('Impossible de charger la liste des membres.');

      },

    });

  }



  onBulkFiltreInput(event: Event): void {

    this.bulkFiltre.set((event.target as HTMLInputElement).value ?? '');

    this.bulkPage.set(1);

  }



  goBulkPage(p: number): void {

    this.bulkPage.set(Math.min(this.bulkTotalPages(), Math.max(1, p)));

  }



  toggleSelectionPageBulk(): void {

    const page = this.membresBulkPage();

    if (this.bulkPageToutSelectionnee()) {

      const ids = new Set(page.map((m) => m.id));

      this.membresBulk.update((list) => list.filter((m) => !ids.has(m.id)));

    } else {

      const existants = new Set(this.membresBulk().map((m) => m.id));

      const ajouts = page.filter((m) => !existants.has(m.id));

      if (ajouts.length > 0) {

        this.membresBulk.update((list) => [...list, ...ajouts]);

      }

    }

  }



  isMembreBulkSelected(id: number): boolean {

    return this.membresBulk().some((m) => m.id === id);

  }



  toggleMembreBulk(m: MembreDto): void {

    if (this.isMembreBulkSelected(m.id)) {

      this.membresBulk.update((list) => list.filter((x) => x.id !== m.id));

    } else {

      this.membresBulk.update((list) => [...list, m]);

    }

  }



  retirerMembreBulk(id: number): void {

    this.membresBulk.update((list) => list.filter((m) => m.id !== id));

  }



  viderMembresBulk(): void {

    this.membresBulk.set([]);

  }



  membreSearchHasQuery(): boolean {

    return (this.form.controls.membreSearch.value ?? '').trim().length > 0;

  }



  onMembreInput(): void {

    this.membreListOpen.set(true);

    const q = (this.form.controls.membreSearch.value ?? '').trim();

    if (!q) {

      this.form.patchValue({ membreId: null });

    }

  }



  onMembreFocus(): void {

    this.membreListOpen.set(true);

  }



  clearMembre(): void {

    this.form.patchValue({ membreId: null, membreSearch: '', membreCodeNumero: '' });

    this.membresRecherche.set([]);

    this.membreListOpen.set(false);

  }



  selectMembre(m: MembreDto): void {

    if (this.modeSaisieMembre() === 'bulk') {

      this.toggleMembreBulk(m);

      return;

    }

    this.form.patchValue({

      membreId: m.id,

      membreSearch: `${m.nomComplet} (${m.codeMembre})`,

    });

    this.form.controls.membreCodeNumero.setValue(suffixeCodeNumerique(m.codeMembre), {

      emitEvent: false,

    });

    this.membreListOpen.set(false);

  }



  rechercherMembreParCodeNumero(afficherErreurSiVide = true): void {

    const numero = (this.form.controls.membreCodeNumero.value ?? '').trim();

    if (!numero || this.orgId < 1) {

      return;

    }

    this.rechercheMembreLoading.set(true);

    this.membreService.rechercher(this.orgId, numero).subscribe({

      next: (list) => {

        const matches = filtrerMembresParNumeroCode(list, numero);

        this.rechercheMembreLoading.set(false);

        if (matches.length === 1) {

          if (this.modeSaisieMembre() === 'bulk') {

            this.toggleMembreBulk(matches[0]);

          } else {

            this.selectMembre(matches[0]);

          }

          return;

        }

        if (matches.length > 1) {

          this.membresRecherche.set(matches);

          this.membreListOpen.set(true);

          this.form.patchValue({ membreSearch: numero, membreId: null }, { emitEvent: false });

          return;

        }

        if (afficherErreurSiVide) {

          this.notify.show('Aucun membre pour ce numéro de code.');

        }

        this.form.patchValue({ membreId: null }, { emitEvent: false });

      },

      error: () => {

        this.rechercheMembreLoading.set(false);

        if (afficherErreurSiVide) {

          this.notify.show('Recherche par numéro impossible.');

        }

      },

    });

  }



  onMembreCodeNumeroEnter(event: Event): void {

    event.preventDefault();

    this.rechercherMembreParCodeNumero();

  }



  selectMotif(m: MotifOption): void {

    this.motifId.set(m.id);

  }



  isMotifOn(m: MotifOption): boolean {

    return this.motifId() === m.id;

  }



  posteLabel(code: string): string {

    const p = postePourCodeMembre(code);

    return `${p.icon} ${p.label}`;

  }



  epargneDisplay(id: number): string {

    const s = this.soldesParMembre().get(id);

    const total = Number(s?.epargneHebdo ?? 0) + Number(s?.epargneMois ?? 0);

    return formatFcfa(total);

  }



  penaliteDisplay(id: number): string {

    const s = this.soldesParMembre().get(id);

    return formatFcfa(Number(s?.penalite ?? 0));

  }



  amendeDisplay(id: number): string {

    const s = this.soldesParMembre().get(id);

    return formatFcfa(Number(s?.amende ?? 0));

  }



  initials(nom: string): string {

    return nom

      .split(' ')

      .filter(Boolean)

      .map((p) => p[0])

      .join('')

      .slice(0, 2)

      .toUpperCase();

  }



  avatarColor(code: string): string {

    const k = postePourCodeMembre(code).kind;

    if (k === 'president') return '#7c3aed';

    if (k === 'sg' || k === 'sga') return '#1e6fa8';

    if (k === 'tresorier') return '#c0392b';

    return 'var(--re)';

  }



  annuler(): void {

    const t = this.typeUi();

    this.form.patchValue({

      montant: t === 'pen' ? 2000 : 5000,

      dateApplication: this.todayIso(),

      observation: '',

      membreId: null,

      membreSearch: '',

      membreCodeNumero: '',

    });

    this.membresRecherche.set([]);

    this.membresBulk.set([]);

    this.motifId.set(t === 'pen' ? 'absence' : 'reglement');

    this.membreListOpen.set(false);

  }



  valider(): void {

    if (this.modeSaisieMembre() === 'bulk') {

      this.validerBulk();

      return;

    }

    if (this.form.invalid || this.form.controls.membreId.value == null || !this.orgId) {

      this.notify.show('Veuillez sélectionner un membre et un montant valide.');

      return;

    }

    this.appliquerPourMembre(this.form.controls.membreId.value, true);

  }



  private validerBulk(): void {

    const membres = this.membresBulk();

    if (membres.length === 0) {

      this.notify.show('Ajoutez au moins un membre à la liste (recherche ou N° code).');

      return;

    }

    if (this.form.controls.montant.invalid || this.form.controls.dateApplication.invalid) {

      this.notify.show('Veuillez vérifier le montant et la date.');

      return;

    }

    const motif = this.motifSelectionne();

    if (!motif) return;



    const typeApi = this.typeUi() === 'pen' ? 'PENALITE' : 'AMENDE';

    const bodyBase: Omit<AppliquerSanctionRequest, 'membreId'> = {

      type: typeApi,

      montant: this.form.controls.montant.value,

      dateOperation: this.form.controls.dateApplication.value,

      motif: motif.label,

      observation: this.form.controls.observation.value || null,

    };



    this.loading.set(true);

    from(membres)

      .pipe(

        concatMap((m) =>

          this.penaliteAmendeApi.appliquer(this.orgId, { ...bodyBase, membreId: m.id }).pipe(

            map(() => ({ membre: m, ok: true as const })),

            catchError((err) =>

              of({

                membre: m,

                ok: false as const,

                message:

                  typeof err?.error?.message === 'string'

                    ? err.error.message

                    : 'Erreur lors de l’enregistrement.',

              })

            )

          )

        ),

        toArray(),

        finalize(() => this.loading.set(false))

      )

      .subscribe((results) => {

        const ok = results.filter((r) => r.ok);

        const ko = results.filter(
          (r): r is { membre: MembreDto; ok: false; message: string } => !r.ok
        );

        const label = this.typeUi() === 'pen' ? 'pénalité' : 'amende';

        if (ko.length === 0) {

          this.notify.show(

            `✅ ${ok.length} ${label}${ok.length > 1 ? 's' : ''} enregistrée${ok.length > 1 ? 's' : ''}.`

          );

          this.annuler();

          this.chargerDonnees();

          return;

        }

        if (ok.length > 0) {

          const codesKo = ko.map((r) => r.membre.codeMembre).join(', ');

          this.notify.show(

            `${ok.length} enregistrée(s), ${ko.length} échec(s) (${codesKo}). Les membres en échec restent dans la liste.`

          );

          const koIds = new Set(ko.map((r) => r.membre.id));

          this.membresBulk.set(membres.filter((m) => koIds.has(m.id)));

          this.chargerDonnees();

          return;

        }

        const first = ko[0];

        this.notify.show(

          `Aucune ${label} enregistrée. ${first.membre.codeMembre} : ${first.message ?? 'erreur'}.`

        );

      });

  }



  private appliquerPourMembre(membreId: number, rechargerApres: boolean): void {

    const motif = this.motifSelectionne();

    if (!motif || !this.orgId) return;



    this.loading.set(true);

    this.penaliteAmendeApi

      .appliquer(this.orgId, {

        membreId,

        type: this.typeUi() === 'pen' ? 'PENALITE' : 'AMENDE',

        montant: this.form.controls.montant.value,

        dateOperation: this.form.controls.dateApplication.value,

        motif: motif.label,

        observation: this.form.controls.observation.value || null,

      })

      .subscribe({

        next: () => {

          this.loading.set(false);

          const msg =

            this.typeUi() === 'pen'

              ? 'Pénalité appliquée avec succès.'

              : 'Amende appliquée avec succès.';

          this.notify.show(msg);

          if (rechargerApres) {

            this.annuler();

            this.chargerDonnees();

          }

        },

        error: (err) => {

          this.loading.set(false);

          this.notify.show(err?.error?.message ?? 'Impossible d\'appliquer la sanction.');

        },

      });

  }



  private todayIso(): string {

    const d = new Date();

    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

  }

}


