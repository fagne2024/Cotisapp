import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';

import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ActivatedRoute, Router } from '@angular/router';

import {
  catchError,
  concatMap,
  debounceTime,
  distinctUntilChanged,
  finalize,
  forkJoin,
  from,
  map,
  of,
  Subscription,
  switchMap,
  toArray,
} from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { ConfirmDialogService } from '../../../core/services/confirm-dialog.service';
import { NotificationService } from '../../../core/services/notification.service';

import { MembreDto, MembreService } from '../../../core/services/membre.service';

import { organisationCouranteId } from '../../../core/util/org-route.util';

import {

  CotisationHebdoRequest,

  CotisationMoisRequest,
  MouvementPreview,

  OperationService,

} from '../../../core/services/operation.service';

import { RegleOperationService } from '../../../core/services/regle-operation.service';
import {
  CotisationHistoriqueLigneDto,
  CotisationPanelService,
  CotisationPanneauDto,
  CotisationRecenteDto,
} from '../../../core/services/cotisation-panel.service';
import {
  REGLE_HEBDO_FALLBACK,
  REGLE_MOIS_FALLBACK,
  regleCotisationDepuisDto,
  RegleCotisationUi,
} from '../../../core/util/regle-cotisation.util';

import { formatFcfa } from '../../../core/utils/currency.util';

import { postePourCodeMembre, postePourMembre, PosteMembreApi } from '../../membres/membres-poste.util';

import { FilterQueryNav, qpEnum, qpString } from '../../../shared/util/filter-query.util';

import { matchTextQuery } from '../../../shared/util/filter.util';
import { ListPaginationComponent } from '../../../shared/components/list-pagination/list-pagination.component';
import {
  clampPage,
  paginateSlice,
  paginationTotalPages,
} from '../../../shared/util/pagination.util';
import {
  filtrerMembresParNumeroCode,
  suffixeCodeNumerique,
} from '../../../shared/util/membre-code-lookup.util';

import { SuiviCotisationRow } from './cotisation-suivi-demo.util';
import { buildMoisOptions, moisCourantKey } from './cotisation-mois.util';
import { normaliserPreviewCotisation, totalCreditPreview } from './cotisation-preview.util';

import {

  buildSemaineOptions,

  montantDefaut,

  montantsParPas,

  semaineCouranteKey,
  buildSemaineOptionsAroundDate,
  semaineKeyFromIsoDate,
  SemaineOption,
} from './cotisation-semaine.util';

import {
  modePartsActif,
  montantDepuisParts,
  nombrePartsDepuisMontant,
  partDefaut,
  presetsPartsCotisation,
  type PartCotisationPreset,
} from '../../../shared/util/parts-cotisation.util';
import {
  MODES_PAIEMENT,
  modePaiementMobile,
  type ModePaiement,
} from '../../../shared/util/mode-paiement.util';
import { DROIT_ACTION_IMPORTS } from '../../../shared/imports/droit-action.imports';

export type CotisationTypeUi = 'hebdo' | 'mois' | 'historique';

@Component({

  selector: 'app-cotisation-mois',

  standalone: true,

  imports: [ReactiveFormsModule, ListPaginationComponent, ...DROIT_ACTION_IMPORTS],

  templateUrl: './cotisation-mois.component.html',

  styleUrls: [
    './cotisation-mois.component.scss',
    '../../../shared/styles/membre-search-row.scss',
    '../../../shared/styles/pagination.scss',
  ],

})

export class CotisationMoisComponent implements OnInit, OnDestroy {

  readonly Math = Math;
  private readonly fb = inject(FormBuilder);

  private readonly route = inject(ActivatedRoute);

  private readonly router = inject(Router);

  private readonly auth = inject(AuthService);

  private readonly operationService = inject(OperationService);

  private readonly membreService = inject(MembreService);

  private readonly regleService = inject(RegleOperationService);

  private readonly panelService = inject(CotisationPanelService);

  private readonly notify = inject(NotificationService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  readonly formatFcfa = formatFcfa;
  readonly modesPaiement = MODES_PAIEMENT;
  readonly modePaiementMobile = modePaiementMobile;

  readonly typeUi = signal<CotisationTypeUi>('hebdo');

  readonly regleHebdo = signal<RegleCotisationUi>(REGLE_HEBDO_FALLBACK);

  readonly regleMois = signal<RegleCotisationUi>(REGLE_MOIS_FALLBACK);

  readonly membresRecherche = signal<MembreDto[]>([]);

  /** Un membre ou sélection multiple (même parts / période / paiement). */
  readonly modeSaisieMembre = signal<'unitaire' | 'bulk'>('unitaire');

  readonly membresBulk = signal<MembreDto[]>([]);

  /** Catalogue complet pour le mode en lot (pagination 10 / page). */
  readonly membresBulkCatalogue = signal<MembreDto[]>([]);

  readonly membresBulkCatalogueLoading = signal(false);

  readonly bulkFiltre = signal('');

  readonly bulkPage = signal(1);

  readonly bulkPageSize = 10;

  readonly rechercheMembreLoading = signal(false);

  readonly previewApi = signal<MouvementPreview[]>([]);

  readonly loading = signal(false);

  readonly membreListOpen = signal(false);

  readonly semaineOptions = signal<SemaineOption[]>(buildSemaineOptions());



  readonly moisOptions = signal(buildMoisOptions());



  readonly postePourCodeMembre = postePourCodeMembre;



  readonly form = this.fb.nonNullable.group({

    membreId: [null as number | null, Validators.required],

    semaineKey: [semaineCouranteKey()],

    moisAnnee: [moisCourantKey(), Validators.required],

    montant: [5000, [Validators.required, Validators.min(1)]],

    nbParts: [1, [Validators.required, Validators.min(1)]],

    dateOperation: [this.todayIso(), Validators.required],

    observation: [''],

    membreSearch: [''],

    membreCodeNumero: [''],

    amendeActive: [false],

    montantAmende: [{ value: null as number | null, disabled: true }],

    modePaiement: ['ESPECES' as ModePaiement],

    referencePaiement: [''],

  });



  readonly regleActive = computed(() => {
    const t = this.typeUi();
    if (t === 'mois') {
      return this.regleMois();
    }
    return this.regleHebdo();
  });



  readonly presetsMontant = computed((): PartCotisationPreset[] => {
    const r = this.regleActive();
    if (modePartsActif(r) && r.montantParPart != null && r.partsMin != null && r.partsMax != null) {
      return presetsPartsCotisation(r.partsMin, r.partsMax, r.montantParPart);
    }
    return montantsParPas(r.montantMin, r.montantMax, 1000).map((m) => ({
      nbParts: 0,
      montant: m,
      label: this.formatFcfa(m),
    }));
  });

  readonly utiliseParts = computed(() => modePartsActif(this.regleActive()));

  readonly plageParts = computed(() => {
    const r = this.regleActive();
    if (!modePartsActif(r) || r.partsMin == null || r.partsMax == null) {
      return null;
    }
    return { min: r.partsMin, max: r.partsMax };
  });

  /** Options de la liste déroulante : de partsMin à partsMax (ex. 1…10). */
  readonly optionsPartsListe = computed(() => {
    const plage = this.plageParts();
    if (!plage) return [];
    const out: number[] = [];
    for (let n = plage.min; n <= plage.max; n++) {
      out.push(n);
    }
    return out;
  });

  /** Montant total affiché (recalculé à chaque cycle CD, pas via computed). */
  montantDepuisPartsAffiche(): number | null {
    const r = this.regleActive();
    if (!modePartsActif(r) || !r.montantParPart) return null;
    const nb = Number(this.form.controls.nbParts.value) || 0;
    return montantDepuisParts(nb, r.montantParPart);
  }

  readonly presetsAmende = computed(() => {
    const r = this.regleActive();
    return montantsParPas(r.montantAmendeMin, r.montantAmendeMax, 100);
  });

  readonly simAmende = computed(() => {
    if (!this.form.controls.amendeActive.value) return 0;
    return Number(this.form.controls.montantAmende.value ?? 0);
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

  readonly bulkNbMembres = computed(() => this.membresBulk().length);

  /** Montant par membre pour le récap lot (recalculé à chaque cycle CD). */
  bulkMontantParMembreAffiche(): number {
    return this.montantDepuisPartsAffiche() ?? (Number(this.form.controls.montant.value) || 0);
  }

  bulkMontantTotalAffiche(): number {
    const n = this.bulkNbMembres();
    const m = this.bulkMontantParMembreAffiche();
    return n > 0 && m > 0 ? n * m : 0;
  }

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
    const selected = new Set(this.membresBulk().map((m) => m.id));
    return page.length > 0 && page.every((m) => selected.has(m.id));
  });



  readonly simSolidarite = computed(() => {

    const r = this.regleActive();

    return r.solidariteAuto ? r.montantSolidarite : 0;

  });



  readonly simEpargne = computed(() => this.form.controls.montant.value);

  readonly simCaisse = computed(() => this.form.controls.montant.value);

  /** Lignes affichées (fusion épargne + caisse si besoin). */
  readonly previewLignes = computed(() =>
    normaliserPreviewCotisation(this.previewApi(), this.selectedMembre()?.nomComplet)
  );

  readonly hasPreview = computed(() => this.previewLignes().length > 0);

  /** Total = somme des lignes affichées (cotisation unique + solidarité). */
  readonly simTotalCredit = computed(() => {
    const lignes = this.previewLignes();
    if (lignes.length > 0) {
      return totalCreditPreview(lignes);
    }
    const m = this.form.controls.montant.value;
    return m + this.simSolidarite() + this.simAmende();
  });



  readonly suiviRows = signal<SuiviCotisationRow[]>([]);
  readonly panneauLabel = signal('');
  readonly cotisationsRecentes = signal<CotisationRecenteDto[]>([]);
  readonly resumeAujourdhui = signal({ nombre: 0, montant: 0 });
  readonly panneauLoading = signal(false);

  readonly filtreSuiviStatut = signal<'tous' | 'paye' | 'attente'>('attente');

  readonly filtreSuiviRecherche = signal('');



  readonly suiviFiltre = computed(() => {

    const st = this.filtreSuiviStatut();

    const q = this.filtreSuiviRecherche();

    return this.suiviRows().filter((r) => {

      if (st !== 'tous' && r.statut !== st) return false;

      return matchTextQuery(q, r.nom, r.sousTitre);

    });

  });



  readonly suiviStats = computed(() => {

    const rows = this.suiviFiltre();

    return {

      payes: rows.filter((r) => r.statut === 'paye').length,

      attente: rows.filter((r) => r.statut === 'attente').length,

    };

  });



  readonly titreSuivi = computed(() => {
    const label = this.panneauLabel();
    if (label) {
      return `📅 ${label} — Suivi`;
    }
    return this.typeUi() === 'hebdo' ? '📅 Suivi hebdomadaire' : '📅 Suivi mensuel';
  });

  readonly historiqueLignes = signal<CotisationHistoriqueLigneDto[]>([]);
  readonly historiqueLoading = signal(false);
  readonly annulationOperationId = signal<number | null>(null);
  readonly filtreHistType = signal<'tous' | 'hebdo' | 'mois' | 'solidarite'>('tous');
  readonly filtreHistRecherche = signal('');
  readonly filtreHistDateDebut = signal('');
  readonly filtreHistDateFin = signal('');

  readonly histPage = signal(1);
  readonly histPageSize = 10;

  readonly historiqueFiltre = computed(() => {
    const type = this.filtreHistType();
    const q = this.filtreHistRecherche();
    const debut = this.filtreHistDateDebut();
    const fin = this.filtreHistDateFin();
    return this.historiqueLignes().filter((l) => {
      const ctx = this.typeCotisationLigne(l);
      if (type === 'hebdo' && ctx !== 'HEBDO') return false;
      if (type === 'mois' && ctx !== 'MOIS') return false;
      if (type === 'solidarite' && l.typeLigne !== 'SOLIDARITE') return false;
      const d = this.dateOperationIso(l.dateOperation);
      if (debut && d && d < debut) return false;
      if (fin && d && d > fin) return false;
      return matchTextQuery(q, l.membreNom, l.codeMembre, l.periode, l.typeLibelle, l.observation ?? '');
    });
  });

  readonly historiquePageItems = computed(() => {
    const items = this.historiqueFiltre();
    const page = clampPage(this.histPage(), paginationTotalPages(items.length, this.histPageSize));
    return paginateSlice(items, page, this.histPageSize);
  });

  readonly histTotalPages = computed(() =>
    paginationTotalPages(this.historiqueFiltre().length, this.histPageSize)
  );

  readonly historiqueTotaux = computed(() => {
    const rows = this.historiqueFiltre().filter((r) => !r.annulee);
    const hebdo = rows.filter((r) => r.typeLigne === 'HEBDO');
    const mois = rows.filter((r) => r.typeLigne === 'MOIS');
    const solidarite = rows.filter((r) => r.typeLigne === 'SOLIDARITE');
    return {
      lignes: rows.length,
      hebdo: hebdo.length,
      mois: mois.length,
      solidarite: solidarite.length,
      montantHebdo: hebdo.reduce((s, r) => s + Number(r.montant), 0),
      montantMois: mois.reduce((s, r) => s + Number(r.montant), 0),
      montantSolidarite: solidarite.reduce((s, r) => s + Number(r.montant), 0),
    };
  });



  private orgId = 0;

  private sub = new Subscription();

  private prevCotisationType: CotisationTypeUi | null = null;

  private readonly queryNav = new FilterQueryNav();

  private readonly queryDefaults = { suivi: 'attente', q: '' };



  @HostListener('window:keydown', ['$event'])
  handleKeyboardEvent(event: KeyboardEvent): void {
    // Ctrl+1: Hebdo tab
    if (event.ctrlKey && event.key === '1') {
      event.preventDefault();
      this.setType('hebdo');
    }
    // Ctrl+2: Mois tab
    if (event.ctrlKey && event.key === '2') {
      event.preventDefault();
      this.setType('mois');
    }
    // Ctrl+3: Historique tab
    if (event.ctrlKey && event.key === '3') {
      event.preventDefault();
      this.setType('historique');
    }
    // Escape: Reset/clear form
    if (event.key === 'Escape') {
      event.preventDefault();
      this.form.reset();
    }
  }

  ngOnInit(): void {

    this.orgId = organisationCouranteId(this.route, this.auth) ?? 0;

    this.semaineOptions.set(buildSemaineOptions());



    this.sub.add(

      this.route.queryParamMap.subscribe((pm) => {

        const t = this.parseTypeUi(pm.get('t'));

        const changed = this.prevCotisationType !== null && this.prevCotisationType !== t;

        this.prevCotisationType = t;

        this.typeUi.set(t);

        if (t === 'historique') {
          this.chargerHistorique();
        } else {
          this.applyTypeRules(changed);
          this.chargerPanneau();
        }

        this.queryNav.runSync(() => {

          this.filtreSuiviStatut.set(

            qpEnum(pm, 'suivi', ['tous', 'paye', 'attente'] as const, 'attente')

          );

          this.filtreSuiviRecherche.set(qpString(pm, 'q'));

        });

      })

    );



    this.sub.add(
      this.regleService.obtenirCotisations(this.orgId).subscribe({
        next: (cot) => {
          this.regleHebdo.set(
            regleCotisationDepuisDto(cot.hebdomadaire, REGLE_HEBDO_FALLBACK)
          );
          this.regleMois.set(regleCotisationDepuisDto(cot.mensuelle, REGLE_MOIS_FALLBACK));
          if (this.typeUi() === 'historique') {
            this.chargerHistorique();
          } else {
            this.applyTypeRules(false);
          }
        },
        error: () => {
          this.regleHebdo.set(REGLE_HEBDO_FALLBACK);
          this.regleMois.set(REGLE_MOIS_FALLBACK);
          if (this.typeUi() === 'historique') {
            this.chargerHistorique();
          } else {
            this.applyTypeRules(false);
          }
        },
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

    this.sub.add(
      this.form.valueChanges.pipe(debounceTime(350)).subscribe(() => {
        if (this.modeSaisieMembre() === 'bulk') {
          if (this.membresBulk().length > 0) {
            this.refreshPreview();
          }
          return;
        }
        if (this.form.controls.membreId.valid) {
          this.refreshPreview();
        }
      })
    );

    this.sub.add(
      this.form.controls.dateOperation.valueChanges.subscribe((dateIso) => {
        this.synchroniserSemaineDepuisDate(dateIso);
      })
    );

    this.sub.add(
      this.form.controls.semaineKey.valueChanges.subscribe(() => {
        if (this.typeUi() === 'hebdo') {
          this.chargerPanneau();
        }
      })
    );

    this.sub.add(
      this.form.controls.moisAnnee.valueChanges.subscribe(() => {
        if (this.typeUi() === 'mois') {
          this.chargerPanneau();
        }
      })
    );

    this.sub.add(
      this.form.controls.nbParts.valueChanges.subscribe(() => this.onNbPartsChange())
    );
  }



  ngOnDestroy(): void {

    this.sub.unsubscribe();

    this.queryNav.destroy();

  }



  private pushFiltersToUrl(debounce = false): void {

    this.queryNav.push(

      this.router,

      this.route,

      { suivi: this.filtreSuiviStatut(), q: this.filtreSuiviRecherche() },

      this.queryDefaults,

      debounce ? 400 : 0

    );

  }



  orgCourante(): number | null {

    return organisationCouranteId(this.route, this.auth);

  }



  setType(t: CotisationTypeUi): void {

    void this.router.navigate([], {

      relativeTo: this.route,

      queryParams: { t },

      queryParamsHandling: 'merge',

      replaceUrl: true,

    });

  }

  private parseTypeUi(raw: string | null): CotisationTypeUi {
    if (raw === 'mois') return 'mois';
    if (raw === 'historique') return 'historique';
    return 'hebdo';
  }

  chargerHistorique(): void {
    if (this.orgId < 1) {
      return;
    }
    this.historiqueLoading.set(true);
    this.panelService.listerHistorique(this.orgId).subscribe({
      next: (lignes) => {
        this.historiqueLignes.set(
          (lignes ?? []).map((l) => ({
            ...l,
            typeCotisation: this.typeCotisationLigne(l),
            dateOperation: this.dateOperationIso(l.dateOperation),
            annulee: !!l.annulee,
            annulable: !!l.annulable,
          }))
        );
        this.resetHistPage();
        this.historiqueLoading.set(false);
      },
      error: (err) => {
        this.historiqueLignes.set([]);
        this.resetHistPage();
        this.historiqueLoading.set(false);
        const msg = err?.error?.message;
        this.notify.error(
          typeof msg === 'string' ? msg : 'Impossible de charger l’historique des cotisations.'
        );
      },
    });
  }

  private typeCotisationLigne(l: CotisationHistoriqueLigneDto): 'HEBDO' | 'MOIS' {
    if (l.typeCotisation === 'MOIS' || l.typeCotisation === 'HEBDO') {
      return l.typeCotisation;
    }
    if (l.typeLigne === 'MOIS') {
      return 'MOIS';
    }
    return 'HEBDO';
  }

  private dateOperationIso(value: string | unknown): string {
    if (typeof value === 'string' && value.length >= 10) {
      return value.slice(0, 10);
    }
    if (Array.isArray(value) && value.length >= 3) {
      const y = value[0];
      const m = String(value[1]).padStart(2, '0');
      const d = String(value[2]).padStart(2, '0');
      return `${y}-${m}-${d}`;
    }
    return '';
  }

  onFiltreHistType(ev: Event): void {
    const v = (ev.target as HTMLSelectElement).value;
    if (v === 'hebdo' || v === 'mois' || v === 'solidarite' || v === 'tous') {
      this.filtreHistType.set(v);
      this.resetHistPage();
    }
  }

  onFiltreHistRecherche(ev: Event): void {
    this.filtreHistRecherche.set((ev.target as HTMLInputElement).value);
    this.resetHistPage();
  }

  onFiltreHistDateDebut(ev: Event): void {
    this.filtreHistDateDebut.set((ev.target as HTMLInputElement).value);
    this.resetHistPage();
  }

  onFiltreHistDateFin(ev: Event): void {
    this.filtreHistDateFin.set((ev.target as HTMLInputElement).value);
    this.resetHistPage();
  }

  reinitialiserFiltreHistDates(): void {
    this.filtreHistDateDebut.set('');
    this.filtreHistDateFin.set('');
    this.resetHistPage();
  }

  goHistPage(p: number): void {
    this.histPage.set(Math.min(this.histTotalPages(), Math.max(1, p)));
  }

  private resetHistPage(): void {
    this.histPage.set(1);
  }

  confirmerAnnulation(h: CotisationHistoriqueLigneDto): void {
    if (!h.annulable || h.annulee || this.orgId < 1) {
      return;
    }
    const paiement =
      h.modePaiementLibelle && h.referencePaiement
        ? `${h.modePaiementLibelle} — réf. ${h.referencePaiement}`
        : h.modePaiementLibelle ?? '';
    void this.confirmDialog
      .confirm({
        title: 'Annuler la cotisation',
        message:
          `Membre : ${h.membreNom}\n` +
          `Période : ${h.periode}\n` +
          (paiement ? `Paiement : ${paiement}\n` : '') +
          `Montant : ${this.formatFcfa(h.montant)}\n\n` +
          'Les écritures comptables seront inversées sur tous les comptes concernés (épargne, caisse, solidarité, amende, etc.).\n\n' +
          'Cette action est définitive pour cette opération.',
        confirmLabel: "Confirmer l'annulation",
        cancelLabel: 'Retour',
        variant: 'danger',
      })
      .then((ok) => {
        if (!ok) {
          return;
        }
        this.executerAnnulation(h);
      });
  }

  private executerAnnulation(h: CotisationHistoriqueLigneDto): void {
    this.annulationOperationId.set(h.operationId);
    this.panelService.annulerOperation(this.orgId, h.operationId).subscribe({
      next: (res) => {
        this.annulationOperationId.set(null);
        this.notify.success(res.message ?? 'Cotisation annulée.');
        this.chargerHistorique();
        if (this.typeUi() !== 'historique') {
          this.chargerPanneau();
        }
      },
      error: (err) => {
        this.annulationOperationId.set(null);
        const m = err?.error?.message;
        this.notify.error(typeof m === 'string' ? m : 'Annulation impossible.');
      },
    });
  }

  estAnnulationEnCours(operationId: number): boolean {
    return this.annulationOperationId() === operationId;
  }



  private applyTypeRules(resetMontant: boolean): void {
    if (this.typeUi() === 'historique') {
      return;
    }

    const r = this.regleActive();

    const mCtl = this.form.controls.montant;

    mCtl.setValidators([

      Validators.required,

      Validators.min(r.montantMin),

      Validators.max(r.montantMax),

    ]);

    if (resetMontant) {
      if (modePartsActif(r) && r.montantParPart != null && r.partsMin != null && r.partsMax != null) {
        const p = partDefaut(r.partsMin, r.partsMax);
        const montant = montantDepuisParts(p, r.montantParPart);
        this.form.patchValue({ nbParts: p, montant });
      } else {
        this.form.patchValue({ montant: montantDefaut(r.montantMin, r.montantMax) });
      }
    }

    this.syncValidatorsParts();

    if (this.typeUi() === 'hebdo') {
      this.synchroniserSemaineDepuisDate(this.form.controls.dateOperation.value, false);
    } else {
      this.form.patchValue({ moisAnnee: moisCourantKey() }, { emitEvent: false });
    }

    mCtl.updateValueAndValidity({ emitEvent: false });

    this.syncAmendeValidators();

    this.refreshPreview();
    this.chargerPanneau();

  }

  toggleAmende(): void {
    this.syncAmendeValidators();
    this.refreshPreview();
  }

  private syncAmendeValidators(): void {
    const r = this.regleActive();
    const ctl = this.form.controls.montantAmende;
    if (this.form.controls.amendeActive.value) {
      ctl.enable({ emitEvent: false });
      ctl.setValidators([
        Validators.required,
        Validators.min(r.montantAmendeMin),
        Validators.max(r.montantAmendeMax),
      ]);
    } else {
      ctl.disable({ emitEvent: false });
      ctl.clearValidators();
      ctl.setValue(null, { emitEvent: false });
    }
    ctl.updateValueAndValidity({ emitEvent: false });
  }

  setMontantAmende(v: number): void {
    if (!this.form.controls.amendeActive.value) return;
    this.form.patchValue({ montantAmende: v });
    this.refreshPreview();
  }



  setMontant(v: number): void {
    const r = this.regleActive();
    if (modePartsActif(r) && r.montantParPart) {
      const nb = nombrePartsDepuisMontant(v, r.montantParPart);
      if (nb != null) {
        this.form.patchValue({ nbParts: nb, montant: v });
      } else {
        this.form.patchValue({ montant: v });
      }
    } else {
      this.form.patchValue({ montant: v });
    }
    this.refreshPreview();
  }

  /** Recalcule le montant à partir du nombre de parts (sans prévisualisation). */
  private syncMontantDepuisParts(): void {
    const r = this.regleActive();
    if (!modePartsActif(r) || !r.montantParPart) return;
    let nb = Math.floor(Number(this.form.controls.nbParts.value) || 0);
    const plage = this.plageParts();
    if (plage) {
      if (nb < plage.min) nb = plage.min;
      if (nb > plage.max) nb = plage.max;
      this.form.controls.nbParts.setValue(nb, { emitEvent: false });
    }
    const montant = montantDepuisParts(nb, r.montantParPart);
    this.form.patchValue({ montant }, { emitEvent: false });
    this.form.controls.montant.updateValueAndValidity({ emitEvent: false });
  }

  /** Valide et borne le nombre de parts (liste déroulante, enregistrement). */
  onNbPartsChange(): void {
    this.syncMontantDepuisParts();
    this.refreshPreview();
  }

  setNbParts(nb: number): void {
    this.form.patchValue({ nbParts: nb });
    this.onNbPartsChange();
  }

  private syncValidatorsParts(): void {
    const r = this.regleActive();
    const partsCtl = this.form.controls.nbParts;
    if (modePartsActif(r) && r.partsMin != null && r.partsMax != null) {
      partsCtl.setValidators([
        Validators.required,
        Validators.min(r.partsMin),
        Validators.max(r.partsMax),
      ]);
    } else {
      partsCtl.clearValidators();
    }
    partsCtl.updateValueAndValidity({ emitEvent: false });
  }

  libellePlageRegle(r: RegleCotisationUi): string {
    if (modePartsActif(r) && r.montantParPart != null && r.partsMin != null && r.partsMax != null) {
      return `${r.partsMin}–${r.partsMax} parts × ${this.formatFcfa(r.montantParPart)} = ${this.formatFcfa(r.montantMin)}–${this.formatFcfa(r.montantMax)}`;
    }
    return `${this.formatFcfa(r.montantMin)} – ${this.formatFcfa(r.montantMax)} FCFA`;
  }

  previewMontantClass(line: MouvementPreview): string {
    if (/amende/i.test(line.libelle)) return 'pu-c';
    if (/solidarit/i.test(line.libelle)) return 'or-c';
    return 'cr-c';
  }



  membreSearchHasQuery(): boolean {
    return (this.form.controls.membreSearch.value ?? '').trim().length > 0;
  }

  onMembreInput(): void {

    this.membreListOpen.set(true);

    const q = (this.form.controls.membreSearch.value ?? '').trim();

    if (!q) {
      if (this.modeSaisieMembre() !== 'bulk') {
        this.form.patchValue({ membreId: null });
      }
      this.membresRecherche.set([]);
    }

  }



  onMembreFocus(): void {

    this.membreListOpen.set(true);

  }



  clearMembre(): void {

    this.form.patchValue({ membreId: null, membreSearch: '', membreCodeNumero: '' });

    this.membresRecherche.set([]);

    this.membreListOpen.set(false);

    this.previewApi.set([]);

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
    this.previewApi.set([]);
  }

  chargerMembresBulkCatalogue(): void {
    if (this.orgId < 1) return;
    this.membresBulkCatalogueLoading.set(true);
    this.membreService.lister(this.orgId).subscribe({
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
    const v = (event.target as HTMLInputElement).value ?? '';
    this.bulkFiltre.set(v);
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
    this.refreshPreview();
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
    this.refreshPreview();
  }

  retirerMembreBulk(id: number): void {
    this.membresBulk.update((list) => list.filter((m) => m.id !== id));
    this.refreshPreview();
  }

  viderMembresBulk(): void {
    this.membresBulk.set([]);
    this.previewApi.set([]);
  }

  nbPartsPourBulk(): number {
    return Number(this.form.controls.nbParts.value) || 0;
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

    this.form.controls.membreCodeNumero.setValue(suffixeCodeNumerique(m.codeMembre), { emitEvent: false });

    this.membreListOpen.set(false);

    this.refreshPreview();
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



  posteLabel(code: string): string {

    const p = postePourCodeMembre(code);

    return `${p.icon} ${p.label}`;

  }



  initials(nomComplet: string): string {

    return nomComplet

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

    return 'var(--g2)';

  }



  refreshPreview(): void {
    if (this.typeUi() === 'historique') {
      return;
    }

    const mid = this.membreIdPourPreview();

    const montant = this.form.controls.montant.value;

    if (mid == null || montant == null || montant < 1 || this.orgId < 1) {
      this.previewApi.set([]);
      return;
    }

    if (this.typeUi() === 'hebdo') {
      this.operationService.previewCotisationHebdo(this.orgId, this.buildRequestHebdo(mid)).subscribe({
        next: (p) =>
          this.previewApi.set(normaliserPreviewCotisation(p, this.nomMembrePourPreview(mid))),
        error: () => this.previewApi.set(this.mockPreview()),
      });
    } else {
      this.operationService.previewCotisationMois(this.orgId, this.buildRequestMois(mid)).subscribe({
        next: (p) =>
          this.previewApi.set(normaliserPreviewCotisation(p, this.nomMembrePourPreview(mid))),
        error: () => this.previewApi.set(this.mockPreview()),
      });
    }
  }

  private membreIdPourPreview(): number | null {
    if (this.modeSaisieMembre() === 'bulk') {
      return this.membresBulk()[0]?.id ?? null;
    }
    return this.form.controls.membreId.value;
  }

  private nomMembrePourPreview(membreId: number): string | undefined {
    const m =
      this.membresBulk().find((x) => x.id === membreId) ??
      this.membresRecherche().find((x) => x.id === membreId);
    return m?.nomComplet;
  }



  annuler(): void {

    const r = this.regleActive();

    this.form.patchValue({

      membreId: null,

      membreSearch: '',

      membreCodeNumero: '',

      moisAnnee: moisCourantKey(),

      semaineKey: semaineCouranteKey(),

      montant: montantDefaut(r.montantMin, r.montantMax),

      dateOperation: this.todayIso(),

      observation: '',

      amendeActive: false,

      montantAmende: null,

    });

    this.syncAmendeValidators();

    this.membresRecherche.set([]);
    this.membresBulk.set([]);

    this.previewApi.set([]);

    this.applyTypeRules(false);

  }



  valider(): void {
    if (this.modeSaisieMembre() === 'bulk') {
      this.validerBulk();
      return;
    }

    if (this.form.invalid || this.form.controls.membreId.value == null) {
      this.showToast('Veuillez sélectionner un membre et des montants valides.');
      return;
    }

    if (!this.validerChampsCommuns()) {
      return;
    }

    this.loading.set(true);

    if (this.typeUi() === 'hebdo') {

      this.operationService.validerCotisationHebdo(this.orgId, this.buildRequestHebdo()).subscribe({

        next: () => {

          this.loading.set(false);

          this.showToast('✅ Cotisation hebdomadaire enregistrée avec succès !');

          this.annuler();
          this.chargerPanneau();

        },

        error: (err) => {

          this.loading.set(false);

          const msg = err?.error?.message;

          this.showToast(typeof msg === 'string' ? msg : 'Erreur lors de l’enregistrement.');

        },

      });

    } else {

      this.operationService.validerCotisationMois(this.orgId, this.buildRequestMois()).subscribe({

        next: () => {

          this.loading.set(false);

          this.showToast('✅ Cotisation mensuelle enregistrée avec succès !');

          this.annuler();
          this.chargerPanneau();

        },

        error: (err) => {

          this.loading.set(false);

          const msg = err?.error?.message;

          this.showToast(typeof msg === 'string' ? msg : 'Erreur lors de l’enregistrement.');

        },

      });

    }

  }

  private validerChampsCommuns(): boolean {
    if (this.form.controls.montant.invalid || this.form.controls.dateOperation.invalid) {
      this.showToast('Veuillez vérifier le montant et la date.');
      return false;
    }
    if (this.form.controls.amendeActive.value && this.form.controls.montantAmende.invalid) {
      this.showToast(
        `Montant amende invalide (${this.formatFcfa(this.regleActive().montantAmendeMin)} – ${this.formatFcfa(this.regleActive().montantAmendeMax)}).`
      );
      return false;
    }
    if (modePaiementMobile(this.form.controls.modePaiement.value) && !this.referencePaiementPourRequete()) {
      this.showToast('Indiquez le n° de transaction Wave ou Orange Money.');
      return false;
    }
    return true;
  }

  private validerBulk(): void {
    const membres = this.membresBulk();
    if (membres.length === 0) {
      this.showToast('Ajoutez au moins un membre à la liste (recherche ou N° code).');
      return;
    }
    if (!this.validerChampsCommuns()) {
      return;
    }
    if (this.utiliseParts()) {
      this.onNbPartsChange();
    }

    const hebdo = this.typeUi() === 'hebdo';
    this.loading.set(true);

    from(membres)
      .pipe(
        concatMap((m) => {
          const call = hebdo
            ? this.operationService.validerCotisationHebdo(this.orgId, this.buildRequestHebdo(m.id))
            : this.operationService.validerCotisationMois(this.orgId, this.buildRequestMois(m.id));
          return call.pipe(
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
          );
        }),
        toArray(),
        finalize(() => this.loading.set(false))
      )
      .subscribe((results) => {
        const ok = results.filter((r) => r.ok);
        const ko = results.filter((r) => !r.ok);
        if (ko.length === 0) {
          this.showToast(
            `✅ ${ok.length} cotisation${ok.length > 1 ? 's' : ''} enregistrée${ok.length > 1 ? 's' : ''} (${this.libellePartsBulk()}).`
          );
          this.annuler();
          this.chargerPanneau();
          return;
        }
        if (ok.length > 0) {
          const codesKo = ko.map((r) => r.membre.codeMembre).join(', ');
          this.showToast(
            `${ok.length} enregistrée(s), ${ko.length} échec(s) (${codesKo}). Les membres en échec restent dans la liste.`
          );
          const koIds = new Set(ko.map((r) => r.membre.id));
          this.membresBulk.set(membres.filter((m) => koIds.has(m.id)));
          this.chargerPanneau();
          return;
        }
        const first = ko[0];
        const msg = first.ok ? 'erreur' : (first.message ?? 'erreur');
        this.showToast(`Aucune cotisation enregistrée. ${first.membre.codeMembre} : ${msg}.`);
      });
  }

  private libellePartsBulk(): string {
    if (!this.utiliseParts()) {
      return this.formatFcfa(this.form.controls.montant.value) + ' / membre';
    }
    const nb = Number(this.form.controls.nbParts.value) || 0;
    return `${nb} part${nb > 1 ? 's' : ''} · ${this.formatFcfa(this.form.controls.montant.value)} / membre`;
  }

  actualiserSim(): void {

    this.refreshPreview();

  }



  private showToast(msg: string): void {
    this.notify.show(msg);
  }



  /** Met à jour la liste et la sélection « Semaine » selon la date jour J (mode hebdo). */
  private synchroniserSemaineDepuisDate(dateIso: string, declencherPanneau = true): void {
    if (this.typeUi() !== 'hebdo') {
      return;
    }
    const key = semaineKeyFromIsoDate(dateIso);
    if (!key) {
      return;
    }
    const d = new Date(
      Number(dateIso.slice(0, 4)),
      Number(dateIso.slice(5, 7)) - 1,
      Number(dateIso.slice(8, 10)),
      12,
      0,
      0,
      0
    );
    this.semaineOptions.set(buildSemaineOptionsAroundDate(d));
    if (this.form.controls.semaineKey.value !== key) {
      this.form.patchValue({ semaineKey: key }, { emitEvent: declencherPanneau });
    } else if (declencherPanneau) {
      this.chargerPanneau();
    }
  }

  private todayIso(): string {

    const d = new Date();

    const y = d.getFullYear();

    const mo = String(d.getMonth() + 1).padStart(2, '0');

    const da = String(d.getDate()).padStart(2, '0');

    return `${y}-${mo}-${da}`;

  }



  private montantAmendePourRequete(): number | undefined {
    const v = this.form.getRawValue();
    if (!v.amendeActive || v.montantAmende == null || v.montantAmende <= 0) {
      return undefined;
    }
    return v.montantAmende;
  }

  private buildRequestHebdo(membreId?: number): CotisationHebdoRequest {
    if (this.utiliseParts()) {
      this.syncMontantDepuisParts();
    }
    const v = this.form.getRawValue();
    const mid = membreId ?? v.membreId!;

    return {

      membreId: mid,

      semaineKey: v.semaineKey,

      montant: v.montant,

      dateOperation: v.dateOperation,

      observation: v.observation || undefined,

      montantAmende: this.montantAmendePourRequete(),

      modePaiement: v.modePaiement,

      referencePaiement: this.referencePaiementPourRequete(),

    };

  }



  private buildRequestMois(membreId?: number): CotisationMoisRequest {
    if (this.utiliseParts()) {
      this.syncMontantDepuisParts();
    }
    const v = this.form.getRawValue();
    const mid = membreId ?? v.membreId!;

    return {

      membreId: mid,

      moisAnnee: v.moisAnnee,

      montant: v.montant,

      dateOperation: v.dateOperation,

      observation: v.observation || undefined,

      montantAmende: this.montantAmendePourRequete(),

      modePaiement: v.modePaiement,

      referencePaiement: this.referencePaiementPourRequete(),

    };

  }

  private referencePaiementPourRequete(): string | undefined {
    const ref = (this.form.getRawValue().referencePaiement ?? '').trim();
    if (!ref) return undefined;
    if (!modePaiementMobile(this.form.getRawValue().modePaiement)) return undefined;
    return ref;
  }

  selectModePaiement(mode: ModePaiement): void {
    this.form.patchValue({ modePaiement: mode });
    this.onModePaiementChange();
  }

  onModePaiementChange(): void {
    if (!modePaiementMobile(this.form.controls.modePaiement.value)) {
      this.form.controls.referencePaiement.setValue('');
    }
  }



  onFiltreSuiviStatut(ev: Event): void {

    const v = (ev.target as HTMLSelectElement).value;

    this.filtreSuiviStatut.set(v === 'paye' || v === 'attente' ? v : 'tous');

    this.pushFiltersToUrl();

  }



  onFiltreSuiviRecherche(ev: Event): void {

    this.filtreSuiviRecherche.set((ev.target as HTMLInputElement).value);

    this.pushFiltersToUrl(true);

  }



  chargerPanneau(): void {
    const type = this.typeUi();
    if (this.orgId < 1 || type === 'historique') {
      return;
    }
    const semaine = this.form.controls.semaineKey.value;
    const mois = this.form.controls.moisAnnee.value;
    this.panneauLoading.set(true);
    this.panelService
      .chargerPanneau(this.orgId, type, {
        semaine: type === 'hebdo' ? semaine : undefined,
        mois: type === 'mois' ? mois : undefined,
      })
      .subscribe({
        next: (p) => this.appliquerPanneau(p),
        error: () => {
          this.panneauLoading.set(false);
          this.suiviRows.set([]);
          this.cotisationsRecentes.set([]);
          this.resumeAujourdhui.set({ nombre: 0, montant: 0 });
        },
      });
  }

  private appliquerPanneau(p: CotisationPanneauDto): void {
    this.panneauLoading.set(false);
    this.panneauLabel.set(p.periodeLabel);
    this.suiviRows.set(
      p.suivi.map((row) => ({
        membreId: row.membreId,
        nom: row.nomComplet,
        sousTitre: row.sousTitre,
        statut: row.statut === 'PAYE' ? 'paye' : 'attente',
        initials: this.initials(row.nomComplet),
        avColor: this.avatarColorForPoste(row.poste, row.codeMembre),
      }))
    );
    this.cotisationsRecentes.set(p.recentes);
    this.resumeAujourdhui.set({
      nombre: p.cotisationsAujourdhui,
      montant: Number(p.montantAujourdhui),
    });
  }

  avatarColorForPoste(poste: string | null | undefined, code: string): string {
    if (poste) {
      const meta = postePourMembre(code, poste as PosteMembreApi);
      return this.avatarColorFromKind(meta.kind);
    }
    return this.avatarColor(code);
  }

  private avatarColorFromKind(kind: string): string {
    if (kind === 'president') return '#7c3aed';
    if (kind === 'sg' || kind === 'sga') return '#1e6fa8';
    if (kind === 'tresorier') return '#c0392b';
    return 'var(--g2)';
  }

  private mockPreview(): MouvementPreview[] {

    const m = this.form.getRawValue().montant;

    const mem = this.selectedMembre();

    const lines: MouvementPreview[] = [
      {
        libelle: `Cotisation — Épargne (${mem?.nomComplet ?? ''}) et Caisse organisation`,
        sens: 'CREDIT',
        montant: m,
      },
    ];

    if (this.simSolidarite() > 0) {

      lines.push({

        libelle: 'Solidarité auto (règle fixe)',

        sens: 'CREDIT',

        montant: this.simSolidarite(),

      });

    }

    if (this.simAmende() > 0) {
      lines.push({
        libelle: 'Amende (optionnelle) — Compte membre',
        sens: 'CREDIT',
        montant: this.simAmende(),
      });
    }

    return lines;

  }

}

