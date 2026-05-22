import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { concatMap, from, of, Subscription } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, finalize, map, startWith, switchMap, toArray } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';
import {
  EmpruntDto,
  EmpruntHistoriqueLigneDto,
  EmpruntService,
  TypeEmprunt,
} from '../../core/services/emprunt.service';
import { HttpErrorResponse } from '@angular/common/http';
import { MembreDto, MembreService } from '../../core/services/membre.service';
import {
  EmpruntsReglesDto,
  RegleOperationService,
} from '../../core/services/regle-operation.service';
import {
  EmpruntTypeUi,
  empruntSansFrais,
  libelleFraisEmprunt,
  libellePenaliteEmprunt,
  regleEmpruntEffective,
} from '../../core/util/regle-emprunt.util';
import {
  calculerAvanceCaisseVersSolidarite,
  calculerDateEcheance,
  repartirMontantsEcheances,
  simulerEmpruntDepuisRegle,
} from '../parametrage/regle-emprunt-calcul.util';
import { DashboardService } from '../../core/services/dashboard.service';
import { NotificationService } from '../../core/services/notification.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import { buildOrgRoute } from '../../core/util/notifications-route.util';
import { formatFcfa } from '../../core/utils/currency.util';
import { postePourCodeMembre } from '../membres/membres-poste.util';
import { FilterQueryNav, qpEnum, qpString } from '../../shared/util/filter-query.util';
import { matchTextQuery } from '../../shared/util/filter.util';
import { empruntEnRetard } from '../remboursements/remboursement-emprunt.util';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import {
  filtrerMembresParNumeroCode,
  suffixeCodeNumerique,
} from '../../shared/util/membre-code-lookup.util';
import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import {
  paginateSlice,
  paginationTotalPages,
} from '../../shared/util/pagination.util';

interface ConfirmDialogView {
  title: string;
  paragraphs: string[];
  variant: 'warn' | 'danger' | 'info';
  showCancel: boolean;
  confirmLabel: string;
}

interface EmpruntPanelItem {
  empruntId: number;
  nom: string;
  sousTitre: string;
  typeEmprunt: TypeEmprunt;
  retard: boolean;
  rembourse: number;
  total: number;
  itemClass: string;
  badgeClass: string;
  badgeText: string;
  progClass: string;
}

export type EmpruntPageTab = EmpruntTypeUi | 'historique';

export interface EcheancierRow {
  numero: number;
  dateLabel: string;
  capital: number;
  total: number;
  numClass: 'g' | 'or' | 'bl';
}

@Component({
  selector: 'app-emprunts-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ConfirmDialogComponent, ListPaginationComponent],
  templateUrl: './emprunts-page.component.html',
  styleUrls: [
    './emprunts-page.component.scss',
    '../../shared/styles/membre-search-row.scss',
    '../../shared/styles/membre-selection-mode.scss',
    '../../shared/styles/pagination.scss',
  ],
})
export class EmpruntsPageComponent implements OnInit, OnDestroy {
  readonly Math = Math;
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly membreService = inject(MembreService);
  private readonly empruntService = inject(EmpruntService);
  private readonly regleService = inject(RegleOperationService);
  private readonly dashboardService = inject(DashboardService);
  private readonly notify = inject(NotificationService);

  readonly formatFcfa = formatFcfa;
  readonly libelleFraisEmprunt = libelleFraisEmprunt;
  readonly libellePenaliteEmprunt = libellePenaliteEmprunt;
  readonly postePourCodeMembre = postePourCodeMembre;
  readonly typeUi = signal<EmpruntPageTab>('etale');
  readonly historiqueLignes = signal<EmpruntHistoriqueLigneDto[]>([]);
  readonly historiqueLoading = signal(false);
  readonly annulationOperationId = signal<number | null>(null);
  readonly filtreHistType = signal<'tous' | TypeEmprunt>('tous');
  readonly filtreHistRecherche = signal('');
  readonly filtreHistDateDebut = signal('');
  readonly filtreHistDateFin = signal('');
  readonly reglesEmprunt = signal<EmpruntsReglesDto | null>(null);
  readonly membresRecherche = signal<MembreDto[]>([]);
  readonly rechercheMembreLoading = signal(false);
  readonly emprunts = signal<EmpruntDto[]>([]);
  readonly membreListOpen = signal(false);
  readonly enregistrement = signal(false);
  readonly confirmDialog = signal<ConfirmDialogView | null>(null);
  private confirmCallback: (() => void) | null = null;

  readonly modeSaisieMembre = signal<'unitaire' | 'bulk'>('unitaire');
  readonly membresBulk = signal<MembreDto[]>([]);
  readonly membresBulkCatalogue = signal<MembreDto[]>([]);
  readonly membresBulkCatalogueLoading = signal(false);
  readonly bulkFiltre = signal('');
  readonly bulkPage = signal(1);
  readonly bulkPageSize = 10;

  readonly form = this.fb.nonNullable.group({
    membreId: [null as number | null, Validators.required],
    membreSearch: [''],
    membreCodeNumero: [''],
    montant: [1000, [Validators.required, Validators.min(1)]],
    nbEcheances: [4, [Validators.min(1), Validators.max(24)]],
    dateOctroi: [this.todayIso(), Validators.required],
    motif: [''],
  });

  readonly filteredMembres = computed(() => this.membresRecherche());

  readonly bulkNbMembres = computed(() => this.membresBulk().length);

  /** Total à rembourser par membre (simulation octroi). */
  readonly bulkMontantParMembre = computed(() => Math.max(0, Number(this.simTotal()) || 0));

  readonly bulkMontantTotalLot = computed(() => {
    const n = this.bulkNbMembres();
    const m = this.bulkMontantParMembre();
    return n > 0 && m > 0 ? n * m : 0;
  });

  readonly bulkNbEcheancesSaisi = computed(() => {
    if (this.typeUi() !== 'etale') return 0;
    return Math.max(1, Number(this.form.controls.nbEcheances.value) || 0);
  });

  readonly membresBulkFiltres = computed(() => {
    const q = this.bulkFiltre().trim();
    const list = this.membresBulkCatalogue();
    if (!q) return list;
    return list.filter(
      (m) =>
        matchTextQuery(q, m.nomComplet, m.codeMembre, m.telephone ?? '', m.prenom ?? '', m.nom ?? '')
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

  readonly regleActive = computed(() => {
    const t = this.typeUi();
    const regleUi: EmpruntTypeUi = t === 'historique' ? 'etale' : t;
    return regleEmpruntEffective(this.reglesEmprunt(), regleUi);
  });

  readonly historiqueFiltre = computed(() => {
    const q = this.filtreHistRecherche().trim().toLowerCase();
    const ft = this.filtreHistType();
    const d0 = this.filtreHistDateDebut();
    const d1 = this.filtreHistDateFin();
    return this.historiqueLignes().filter((l) => {
      if (ft !== 'tous' && l.typeEmprunt !== ft) return false;
      if (d0 && l.dateOperation < d0) return false;
      if (d1 && l.dateOperation > d1) return false;
      if (!q) return true;
      const hay = [l.membreNom, l.codeMembre, l.typeLibelle, l.dateLabel, l.observation ?? '']
        .join(' ')
        .toLowerCase();
      return hay.includes(q);
    });
  });

  readonly historiqueTotaux = computed(() => {
    const rows = this.historiqueFiltre().filter((r) => !r.annulee);
    const sum = (type: TypeEmprunt) =>
      rows.filter((r) => r.typeEmprunt === type).reduce((a, r) => a + (r.montantTotal || 0), 0);
    const count = (type: TypeEmprunt) => rows.filter((r) => r.typeEmprunt === type).length;
    return {
      etale: count('ETALE'),
      caisse: count('CAISSE'),
      solidarite: count('SOLIDARITE'),
      montantEtale: sum('ETALE'),
      montantCaisse: sum('CAISSE'),
      montantSolidarite: sum('SOLIDARITE'),
    };
  });

  readonly totalEncours = computed(() =>
    this.emprunts()
      .filter((e) => e.statut === 'EN_COURS')
      .reduce((a, e) => a + (Number(e.montantRestant) || 0), 0)
  );

  readonly selectedMembre = computed(() => {
    const id = this.form.controls.membreId.value;
    if (id == null) return null;
    return this.membresRecherche().find((m) => m.id === id) ?? null;
  });

  /** Emprunt EN_COURS du membre pour le type demandé (onglet) — bloque un second octroi du même type uniquement. */
  readonly empruntEnCoursMembreSelectionne = computed(() => {
    const id = this.form.controls.membreId.value;
    if (id == null) return null;
    const typeDemande = this.typeEmpruntPourOctroiCourant();
    if (typeDemande == null) return null;
    return (
      this.emprunts().find(
        (e) => e.membreId === id && e.statut === 'EN_COURS' && e.typeEmprunt === typeDemande
      ) ?? null
    );
  });

  readonly octroiBloqueEmpruntEnCours = computed(() => this.empruntEnCoursMembreSelectionne() != null);

  readonly compteMembreSimLabel = computed(() => {
    switch (this.typeUi()) {
      case 'sol':
        return 'Solidarité';
      case 'caisse':
        return 'Épargne hebdo';
      default:
        return 'Épargne mois';
    }
  });

  readonly enCoursCount = computed(() => this.emprunts().filter((e) => e.statut === 'EN_COURS').length);
  readonly retardCount = computed(() =>
    this.emprunts().filter((e) => e.statut === 'EN_COURS' && this.empruntEnRetard(e)).length
  );

  readonly filtrePanelType = signal<'tous' | TypeEmprunt>('tous');
  readonly filtrePanelStatut = signal<'tous' | 'retard' | 'cours'>('tous');
  readonly filtrePanelRecherche = signal('');

  readonly panelItemsFiltres = computed(() => {
    const items: EmpruntPanelItem[] = this.emprunts()
      .filter((e) => e.statut === 'EN_COURS')
      .map((e) => this.toPanelItem(e));
    const ft = this.filtrePanelType();
    const fs = this.filtrePanelStatut();
    const q = this.filtrePanelRecherche();
    return items.filter((item) => {
      if (ft !== 'tous' && item.typeEmprunt !== ft) return false;
      if (fs === 'retard' && !item.retard) return false;
      if (fs === 'cours' && item.retard) return false;
      return matchTextQuery(q, item.nom, item.sousTitre);
    });
  });

  readonly sousTitre = computed(() => {
    const ec = this.enCoursCount();
    const rt = this.retardCount();
    if (this.emprunts().length === 0) {
      return '8 en cours · 2 en retard · Choisissez le type d\'emprunt';
    }
    return `${ec} en cours · ${rt} en retard · Choisissez le type d'emprunt`;
  });

  readonly echRows = signal<EcheancierRow[]>([]);
  readonly simFrais = signal(0);
  readonly simTotal = signal(1000);
  readonly simPerEch = signal(38_333);
  readonly simPenalite = signal(0);
  readonly simPaiementUnique = signal(false);
  readonly simFraisLabel = signal('');
  readonly simMv1 = signal('');
  readonly simMv2 = signal('');
  readonly simNote = signal('');
  readonly simAvanceCaisse = signal(0);
  readonly soldeSolidariteOrg = signal<number | null>(null);
  readonly soldeCaisseOrg = signal<number | null>(null);

  private orgId = 0;
  private sub = new Subscription();
  private readonly queryNav = new FilterQueryNav();
  private readonly queryDefaults = { empType: 'tous', empStatut: 'tous', q: '' };

  @HostListener('window:keydown', ['$event'])
  handleKeyboardEvent(event: KeyboardEvent): void {
    // Ctrl+1: Étalé
    if (event.ctrlKey && event.key === '1') {
      event.preventDefault();
      this.setType('etale');
    }
    // Ctrl+2: Caisse
    if (event.ctrlKey && event.key === '2') {
      event.preventDefault();
      this.setType('caisse');
    }
    // Ctrl+3: Solidarité
    if (event.ctrlKey && event.key === '3') {
      event.preventDefault();
      this.setType('sol');
    }
    // Ctrl+4: Historique
    if (event.ctrlKey && event.key === '4') {
      event.preventDefault();
      this.setType('historique');
    }
  }

  ngOnInit(): void {
    this.orgId = organisationCouranteId(this.route, this.auth) ?? 0;

    this.sub.add(
      this.route.queryParamMap.subscribe((pm) => {
        const tab = this.parsePageTab(pm.get('t'));
        if (tab !== this.typeUi()) {
          this.typeUi.set(tab);
          if (tab === 'historique') {
            this.chargerHistorique();
          } else {
            this.appliquerContraintesRegle();
            this.recalc();
          }
        }
        this.queryNav.runSync(() => {
          this.filtrePanelType.set(
            qpEnum(pm, 'empType', ['tous', 'ETALE', 'CAISSE', 'SOLIDARITE'] as const, 'tous')
          );
          this.filtrePanelStatut.set(
            qpEnum(pm, 'empStatut', ['tous', 'retard', 'cours'] as const, 'tous')
          );
          this.filtrePanelRecherche.set(qpString(pm, 'q'));
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

    this.sub.add(
      this.empruntService.lister(this.orgId).subscribe({
        next: (list) => this.emprunts.set(list),
        error: () => this.emprunts.set([]),
      })
    );

    this.sub.add(
      this.dashboardService.obtenir(this.orgId).subscribe({
        next: (d) => {
          this.soldeSolidariteOrg.set(Number(d.soldeSolidarite ?? 0));
          this.soldeCaisseOrg.set(Number(d.soldeCaisse ?? 0));
          this.recalc();
        },
        error: () => {
          this.soldeSolidariteOrg.set(null);
          this.soldeCaisseOrg.set(null);
        },
      })
    );

    this.sub.add(
      this.regleService.obtenirEmprunts(this.orgId).subscribe({
        next: (dto) => {
          this.reglesEmprunt.set(dto);
          this.appliquerContraintesRegle();
          this.recalc();
        },
        error: () => {
          this.reglesEmprunt.set(null);
          this.appliquerContraintesRegle();
          this.recalc();
        },
      })
    );

    this.sub.add(
      this.form.valueChanges.pipe(startWith(this.form.getRawValue())).subscribe(() => this.recalc())
    );
    this.appliquerContraintesRegle();
    this.recalc();
    if (this.typeUi() === 'historique') {
      this.chargerHistorique();
    }
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
        empType: this.filtrePanelType(),
        empStatut: this.filtrePanelStatut(),
        q: this.filtrePanelRecherche(),
      },
      this.queryDefaults,
      debounce ? 400 : 0
    );
  }

  orgCourante(): number | null {
    return organisationCouranteId(this.route, this.auth);
  }

  lienParametrageRegles(): (string | number)[] {
    const id = this.orgCourante();
    if (id == null) {
      return ['/organisations', 0, 'parametrage', 'regles'];
    }
    return buildOrgRoute(this.router, id, ['parametrage', 'regles']);
  }

  allerAuSuiviEmprunts(): void {
    const oid = this.orgId > 0 ? this.orgId : this.orgCourante();
    if (oid == null) return;
    void this.router.navigate(['/organisations', oid, 'operations', 'emprunts', 'suivi']);
  }

  setType(t: EmpruntPageTab): void {
    if (t === this.typeUi()) return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { t },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private parsePageTab(raw: string | null): EmpruntPageTab {
    if (raw === 'caisse' || raw === 'sol' || raw === 'historique') return raw;
    return 'etale';
  }

  chargerHistorique(): void {
    if (this.orgId < 1) return;
    this.historiqueLoading.set(true);
    this.empruntService.listerHistorique(this.orgId).subscribe({
      next: (lignes) => {
        this.historiqueLignes.set(
          (lignes ?? []).map((l) => ({
            ...l,
            dateOperation: this.dateOperationIso(l.dateOperation),
            annulee: !!l.annulee,
            annulable: !!l.annulable,
          }))
        );
        this.historiqueLoading.set(false);
      },
      error: (err) => {
        this.historiqueLignes.set([]);
        this.historiqueLoading.set(false);
        const msg = err?.error?.message;
        this.notify.error(
          typeof msg === 'string' ? msg : 'Impossible de charger l\'historique des emprunts.'
        );
      },
    });
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
    if (v === 'ETALE' || v === 'CAISSE' || v === 'SOLIDARITE' || v === 'tous') {
      this.filtreHistType.set(v);
    }
  }

  onFiltreHistRecherche(ev: Event): void {
    this.filtreHistRecherche.set((ev.target as HTMLInputElement).value);
  }

  onFiltreHistDateDebut(ev: Event): void {
    this.filtreHistDateDebut.set((ev.target as HTMLInputElement).value);
  }

  onFiltreHistDateFin(ev: Event): void {
    this.filtreHistDateFin.set((ev.target as HTMLInputElement).value);
  }

  reinitialiserFiltreHistDates(): void {
    this.filtreHistDateDebut.set('');
    this.filtreHistDateFin.set('');
  }

  estAnnulationEnCours(operationId: number): boolean {
    return this.annulationOperationId() === operationId;
  }

  confirmerAnnulationEmprunt(h: EmpruntHistoriqueLigneDto): void {
    if (!h.annulable || h.annulee || this.orgId < 1) return;
    this.ouvrirConfirmDialog(
      {
        title: 'Annuler l\'octroi d\'emprunt',
        paragraphs: [
          `Annuler l'octroi pour ${h.membreNom} (${formatFcfa(h.montantTotal)}) ?`,
          'Les écritures comptables seront inversées et l\'emprunt sera marqué annulé.',
          'Impossible si des remboursements existent déjà sur cet emprunt.',
        ],
        variant: 'danger',
        showCancel: true,
        confirmLabel: 'Confirmer l\'annulation',
      },
      () => this.executerAnnulationEmprunt(h)
    );
  }

  private executerAnnulationEmprunt(h: EmpruntHistoriqueLigneDto): void {
    this.annulationOperationId.set(h.operationId);
    this.empruntService.annulerOctroi(this.orgId, h.operationId).subscribe({
      next: (res) => {
        this.annulationOperationId.set(null);
        this.notify.success(res.message ?? 'Emprunt annulé.');
        this.chargerHistorique();
        this.empruntService.lister(this.orgId).subscribe({
          next: (list) => this.emprunts.set(list),
        });
      },
      error: (err) => {
        this.annulationOperationId.set(null);
        const m = err?.error?.message;
        this.notify.error(typeof m === 'string' ? m : 'Annulation impossible.');
      },
    });
  }

  montantMin(): number {
    return Number(this.regleActive().montantMin ?? 1);
  }

  montantMax(): number {
    return Number(this.regleActive().montantMax ?? 9_999_999_999);
  }

  nbEcheancesMin(): number {
    return Number(this.regleActive().nbEcheancesMin ?? 1);
  }

  nbEcheancesMax(): number {
    return Number(this.regleActive().nbEcheancesMax ?? 24);
  }

  private appliquerContraintesRegle(): void {
    const r = this.regleActive();
    const minM = Number(r.montantMin ?? 1);
    const maxM = Number(r.montantMax ?? 9_999_999_999);
    this.form.controls.montant.setValidators([
      Validators.required,
      Validators.min(minM),
      Validators.max(maxM),
    ]);
    const curM = Number(this.form.controls.montant.value);
    if (!curM || curM < minM) {
      this.form.patchValue({ montant: minM }, { emitEvent: false });
    } else if (curM > maxM) {
      this.form.patchValue({ montant: maxM }, { emitEvent: false });
    }

    const tipoUi = this.typeUi();
    if (tipoUi === 'historique') return;
    if (tipoUi === 'etale') {
      const nbMin = Number(r.nbEcheancesMin ?? 1);
      const nbMax = Number(r.nbEcheancesMax ?? 24);
      const nbDef = Number(r.nbEcheancesDefaut ?? nbMin);
      this.form.controls.nbEcheances.setValidators([
        Validators.required,
        Validators.min(nbMin),
        Validators.max(nbMax),
      ]);
      const curNb = Number(this.form.controls.nbEcheances.value);
      if (!curNb || curNb < nbMin || curNb > nbMax) {
        this.form.patchValue({ nbEcheances: nbDef }, { emitEvent: false });
      }
    }
    this.form.controls.montant.updateValueAndValidity({ emitEvent: false });
    this.form.controls.nbEcheances.updateValueAndValidity({ emitEvent: false });
  }

  membreSearchHasQuery(): boolean {
    return (this.form.controls.membreSearch.value ?? '').trim().length > 0;
  }

  onMembreInput(): void {
    this.membreListOpen.set(true);
    const q = (this.form.controls.membreSearch.value ?? '').trim();
    if (!q) {
      this.form.patchValue({ membreId: null });
      this.membresRecherche.set([]);
    }
  }

  onMembreFocus(): void {
    this.membreListOpen.set(true);
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
    this.recalc();
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
    this.recalc();
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
    this.recalc();
  }

  retirerMembreBulk(id: number): void {
    this.membresBulk.update((list) => list.filter((m) => m.id !== id));
    this.recalc();
  }

  viderMembresBulk(): void {
    this.membresBulk.set([]);
    this.recalc();
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
    const typeDemande = this.typeEmpruntPourOctroiCourant();
    if (typeDemande != null) {
      const encours = this.emprunts().find(
        (e) => e.membreId === m.id && e.statut === 'EN_COURS' && e.typeEmprunt === typeDemande
      );
      if (encours) {
        this.showToast(
          `Emprunt ${this.libelleTypeEmprunt(encours.typeEmprunt)} en cours (n° ${encours.id}). Octroi impossible tant qu'il n'est pas soldé pour ce type.`
        );
      }
    }
    this.recalc();
  }

  libelleTypeEmprunt(type: TypeEmprunt): string {
    const labels: Record<TypeEmprunt, string> = {
      ETALE: 'étalé',
      CAISSE: 'caisse',
      SOLIDARITE: 'solidarité',
    };
    return labels[type] ?? type;
  }

  rechercherMembreParCodeNumero(afficherErreurSiVide = true): void {
    const numero = (this.form.controls.membreCodeNumero.value ?? '').trim();
    if (!numero) {
      if (afficherErreurSiVide) return;
      return;
    }
    if (this.orgId < 1) return;

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
    return 'var(--g2)';
  }

  toPanelItem(emp: EmpruntDto): EmpruntPanelItem {
    const retard = this.empruntEnRetard(emp);
    return {
      empruntId: emp.id,
      nom: emp.membreNom,
      sousTitre: `${this.typeLabel(emp.typeEmprunt)} · ${emp.codeMembre}`,
      typeEmprunt: emp.typeEmprunt,
      retard,
      rembourse: emp.montantRembourse,
      total: emp.montantTotal,
      itemClass: this.panelItemClass(emp),
      badgeClass: this.panelBadgeClass(emp),
      badgeText: this.panelBadgeText(emp),
      progClass: this.progFillClass(emp),
    };
  }

  panelProgressPct(item: EmpruntPanelItem): number {
    if (!item.total) return 0;
    return Math.min(100, Math.round((item.rembourse / item.total) * 100));
  }

  onFiltrePanelType(ev: Event): void {
    const v = (ev.target as HTMLSelectElement).value;
    this.filtrePanelType.set(
      v === 'ETALE' || v === 'CAISSE' || v === 'SOLIDARITE' ? v : 'tous'
    );
    this.pushFiltersToUrl();
  }

  onFiltrePanelStatut(ev: Event): void {
    const v = (ev.target as HTMLSelectElement).value;
    this.filtrePanelStatut.set(v === 'retard' || v === 'cours' ? v : 'tous');
    this.pushFiltersToUrl();
  }

  onFiltrePanelRecherche(ev: Event): void {
    this.filtrePanelRecherche.set((ev.target as HTMLInputElement).value);
    this.pushFiltersToUrl(true);
  }

  typeLabel(t: TypeEmprunt): string {
    const m: Record<TypeEmprunt, string> = {
      ETALE: 'Étalé',
      SOLIDARITE: 'Solidarité',
      CAISSE: 'Caisse',
    };
    return m[t] ?? t;
  }

  readonly empruntEnRetard = empruntEnRetard;

  rembourserEmprunt(item: EmpruntPanelItem): void {
    const t =
      item.typeEmprunt === 'SOLIDARITE'
        ? 'solidarite'
        : item.typeEmprunt === 'CAISSE'
          ? 'caisse'
          : 'etale';
    void this.router.navigate(['/organisations', this.orgId, 'operations', 'remboursements'], {
      queryParams: { t, empruntId: item.empruntId },
      queryParamsHandling: 'merge',
    });
  }

  progressPct(emp: EmpruntDto): number {
    if (!emp.montantTotal) return 0;
    return Math.min(100, Math.round((emp.montantRembourse / emp.montantTotal) * 100));
  }

  panelItemClass(emp: EmpruntDto): string {
    if (this.empruntEnRetard(emp)) return 'retard';
    if (emp.typeEmprunt === 'SOLIDARITE') return 'sol';
    return 'cours';
  }

  panelBadgeClass(emp: EmpruntDto): string {
    if (this.empruntEnRetard(emp)) return 'b-red';
    if (emp.typeEmprunt === 'SOLIDARITE') return 'b-blue';
    if (emp.typeEmprunt === 'CAISSE') return 'b-or';
    return 'b-green';
  }

  panelBadgeText(emp: EmpruntDto): string {
    if (this.empruntEnRetard(emp)) return '⚠ Retard';
    if (emp.typeEmprunt === 'SOLIDARITE') return 'Solidarité';
    if (emp.typeEmprunt === 'CAISSE') return 'Caisse';
    return 'En cours';
  }

  progFillClass(emp: EmpruntDto): string {
    if (this.empruntEnRetard(emp)) return 'red';
    if (emp.typeEmprunt === 'SOLIDARITE') return 'blue';
    return 'green';
  }

  recalc(): void {
    const tipo = this.typeUi();
    if (tipo === 'historique') return;
    const regle = this.regleActive();
    const v = this.form.getRawValue();
    const m = Math.max(0, Number(v.montant) || 0);
    const nbSaisi = tipo === 'etale' ? Number(v.nbEcheances) : undefined;
    const sim = simulerEmpruntDepuisRegle(regle, m, nbSaisi);
    const nech = sim.nbEcheances;

    let mv1 = '';
    let mv2 = '';
    let note = '';

    const sansFrais = empruntSansFrais(regle);
    const debitOrg = m + sim.frais;
    const fraisMv = sansFrais ? '' : ` · Frais : − ${formatFcfa(sim.frais)}`;
    let avanceSol = 0;
    if (tipo === 'sol') {
      const soldeSol = this.soldeSolidariteOrg();
      const avance =
        soldeSol != null ? calculerAvanceCaisseVersSolidarite(soldeSol, debitOrg) : null;
      if (avance != null && avance > 0) {
        avanceSol = avance;
        const debitSol = Math.max(0, debitOrg - avance);
        mv1 = `− ${formatFcfa(avance)} Caisse (avance) · − ${formatFcfa(debitSol)} Solidarité (fonds propre)`;
        mv2 = `+ ${formatFcfa(m)} crédité membre${fraisMv}`;
        const soldeCaisse = this.soldeCaisseOrg();
        if (soldeCaisse != null && soldeCaisse < avance) {
          note = `⚠ Caisse insuffisante pour l'avance (disponible : ${formatFcfa(soldeCaisse)}).`;
        } else {
          note =
            '✓ Caisse et Solidarité débitées chacune de leur part ; au remboursement, chaque compte est recrédité séparément.';
        }
      } else {
        mv1 = sansFrais
          ? `− ${formatFcfa(debitOrg)} (Solidarité)`
          : `− ${formatFcfa(debitOrg)} (Solidarité : capital + frais)`;
        mv2 = `+ ${formatFcfa(m)} crédité membre${fraisMv}`;
        note =
          soldeSol != null
            ? '✓ Solde Solidarité suffisant : pas d\'avance Caisse.'
            : '✓ Si le solde Solidarité est insuffisant, la différence est débitée en Caisse.';
      }
    } else {
      mv1 = sansFrais
        ? `− ${formatFcfa(debitOrg)} (Caisse)`
        : `− ${formatFcfa(debitOrg)} (Caisse : capital + frais)`;
      mv2 = `+ ${formatFcfa(m)} crédité membre${fraisMv}`;
      note = '✓ La caisse ne peut pas être débitée au-delà de son solde disponible.';
    }

    this.simFrais.set(sim.frais);
    this.simTotal.set(sim.totalRembourser);
    this.simPerEch.set(sim.montantParEcheance);
    this.simPenalite.set(sim.penalite);
    this.simPaiementUnique.set(sim.paiementUnique);
    this.simFraisLabel.set(libelleFraisEmprunt(regle));
    this.simMv1.set(mv1);
    this.simMv2.set(mv2);
    this.simNote.set(note);
    this.simAvanceCaisse.set(avanceSol);

    if (nech > 0 && (tipo === 'etale' || sim.paiementUnique)) {
      const rows: EcheancierRow[] = [];
      const base = v.dateOctroi || this.todayIso();
      const montantsEch = sim.montantsEcheances.length
        ? sim.montantsEcheances
        : repartirMontantsEcheances(
            sim.totalRembourser,
            nech,
            regle.montantEcheanceMin,
            regle.montantEcheanceMax
          );
      const totalRemb = sim.totalRembourser > 0 ? sim.totalRembourser : 1;
      for (let i = 0; i < nech; i++) {
        const echTotal = montantsEch[i] ?? 0;
        const numClass: 'g' | 'or' | 'bl' = i === 0 ? 'g' : i === 1 ? 'or' : 'bl';
        const capitalLigne = sim.paiementUnique
          ? m
          : Math.round((echTotal * m) / totalRemb);
        rows.push({
          numero: i + 1,
          dateLabel: sim.paiementUnique
            ? 'Paiement unique'
            : this.formatDateEcheanceLabel(
                calculerDateEcheance(base, i + 1, regle.jourEcheanceMois)
              ),
          capital: capitalLigne,
          total: echTotal,
          numClass,
        });
      }
      this.echRows.set(rows);
    } else {
      this.echRows.set([]);
    }
  }

  countSolidarite(): number {
    return this.emprunts().filter((e) => e.typeEmprunt === 'SOLIDARITE').length;
  }

  annuler(): void {
    const r = this.regleActive();
    this.form.reset({
      membreId: null,
      membreSearch: '',
      membreCodeNumero: '',
      montant: Number(r.montantMin ?? 1000),
      nbEcheances: Number(r.nbEcheancesDefaut ?? 4),
      dateOctroi: this.todayIso(),
      motif: '',
    });
    this.membresRecherche.set([]);
    this.membreListOpen.set(false);
    this.membresBulk.set([]);
    this.modeSaisieMembre.set('unitaire');
    this.form.controls.membreId.setValidators(Validators.required);
    this.form.controls.membreId.updateValueAndValidity({ emitEvent: false });
    this.recalc();
  }

  valider(): void {
    if (this.modeSaisieMembre() === 'bulk') {
      this.validerBulk();
      return;
    }
    if (!this.regleActive().actif) {
      this.showToast('Cette règle d\'emprunt est inactive. Activez-la dans le paramétrage.');
      return;
    }
    if (this.form.invalid || this.form.controls.membreId.value == null) {
      const r = this.regleActive();
      this.showToast(
        `Vérifiez le membre et le montant (${formatFcfa(this.montantMin())} – ${formatFcfa(this.montantMax())}).`
      );
      return;
    }
    const encours = this.empruntEnCoursMembreSelectionne();
    if (encours) {
      this.showToast(
        `Ce membre a déjà un emprunt ${this.libelleTypeEmprunt(encours.typeEmprunt)} en cours (n° ${encours.id}). Remboursez-le avant d'en octroyer un autre du même type.`
      );
      return;
    }
    const m = Number(this.form.controls.montant.value);
    if (m < this.montantMin() || m > this.montantMax()) {
      this.showToast(
        `Montant hors limites : ${formatFcfa(this.montantMin())} – ${formatFcfa(this.montantMax())}.`
      );
      return;
    }
    const tipo = this.typeUi();
    if (tipo === 'historique') return;

    if (tipo === 'sol') {
      const regle = this.regleActive();
      const sim = simulerEmpruntDepuisRegle(regle, m, undefined);
      const debitOrg = m + sim.frais;
      const soldeSol = this.soldeSolidariteOrg() ?? 0;
      const avance = calculerAvanceCaisseVersSolidarite(soldeSol, debitOrg);
      if (avance > 0) {
        this.ouvrirConfirmDialog(
          {
            title: 'Avance Caisse — Solidarité insuffisante',
            paragraphs: [
              `Le solde Solidarité (${formatFcfa(soldeSol)}) est insuffisant pour décaisser ${formatFcfa(debitOrg)}.`,
              `Une avance de ${formatFcfa(avance)} sera débitée en Caisse (même montant restitué en priorité lors des remboursements).`,
              `Débit Caisse : ${formatFcfa(avance)} · Débit Solidarité (fonds propre) : ${formatFcfa(debitOrg - avance)}.`,
              'Cette opération sera tracée. Les remboursements recréditent la Caisse et le fonds Solidarité séparément.',
            ],
            variant: 'warn',
            showCancel: true,
            confirmLabel: 'Confirmer l\'octroi',
          },
          () => this.executerAccorderEmprunt(tipo, m)
        );
        return;
      }
    }

    this.executerAccorderEmprunt(tipo, m);
  }

  private validerBulk(): void {
    const membres = this.membresBulk();
    if (membres.length === 0) {
      this.showToast('Ajoutez au moins un membre à la liste.');
      return;
    }
    if (!this.regleActive().actif) {
      this.showToast('Cette règle d\'emprunt est inactive. Activez-la dans le paramétrage.');
      return;
    }
    const tipo = this.typeUi();
    if (tipo === 'historique') return;
    const m = Number(this.form.controls.montant.value);
    if (m < this.montantMin() || m > this.montantMax()) {
      this.showToast(
        `Montant hors limites : ${formatFcfa(this.montantMin())} – ${formatFcfa(this.montantMax())}.`
      );
      return;
    }
    if (this.form.controls.dateOctroi.invalid) {
      this.showToast('Vérifiez la date d\'octroi.');
      return;
    }

    const typeDemande = this.typeEmpruntApi(tipo);
    const bloques = membres.filter((mem) =>
      this.emprunts().some(
        (e) => e.membreId === mem.id && e.statut === 'EN_COURS' && e.typeEmprunt === typeDemande
      )
    );
    if (bloques.length === membres.length) {
      this.showToast('Tous les membres sélectionnés ont déjà un emprunt en cours pour ce type.');
      return;
    }

    this.enregistrement.set(true);
    const nbEch = tipo === 'etale' ? Number(this.form.controls.nbEcheances.value) : undefined;
    const dateOctroi = this.form.controls.dateOctroi.value;
    const observation = this.form.controls.motif.value?.trim() || undefined;

    from(membres)
      .pipe(
        concatMap((mem) => {
          const encours = this.emprunts().find(
            (e) => e.membreId === mem.id && e.statut === 'EN_COURS' && e.typeEmprunt === typeDemande
          );
          if (encours) {
            return of({
              membre: mem,
              ok: false as const,
              message: `Emprunt ${this.libelleTypeEmprunt(typeDemande)} déjà en cours (n° ${encours.id}).`,
            });
          }
          return this.empruntService
            .accorder(this.orgId, {
              membreId: mem.id,
              typeEmprunt: typeDemande,
              montant: m,
              nbEcheances: nbEch,
              dateOctroi,
              observation,
            })
            .pipe(
              map(() => ({ membre: mem, ok: true as const })),
              catchError((err: HttpErrorResponse) =>
                of({
                  membre: mem,
                  ok: false as const,
                  message:
                    typeof err.error === 'object' && err.error && 'message' in err.error
                      ? String((err.error as { message: string }).message)
                      : 'Impossible d\'accorder l\'emprunt.',
                })
              )
            );
        }),
        toArray(),
        finalize(() => this.enregistrement.set(false))
      )
      .subscribe((results) => {
        const ok = results.filter((r) => r.ok);
        const ko = results.filter((r) => !r.ok);
        this.empruntService.lister(this.orgId).subscribe({
          next: (list) => this.emprunts.set(list),
          error: () => this.emprunts.set([]),
        });
        if (ko.length === 0) {
          this.showToast(
            `✅ ${ok.length} emprunt${ok.length > 1 ? 's' : ''} accordé${ok.length > 1 ? 's' : ''}.`
          );
          this.annuler();
          return;
        }
        if (ok.length > 0) {
          const codesKo = ko.map((r) => r.membre.codeMembre).join(', ');
          this.showToast(
            `${ok.length} accordé(s), ${ko.length} échec(s) (${codesKo}). Les membres en échec restent dans la liste.`
          );
          const koIds = new Set(ko.map((r) => r.membre.id));
          this.membresBulk.set(membres.filter((m) => koIds.has(m.id)));
          return;
        }
        const first = ko[0];
        this.showToast(
          `Aucun emprunt accordé. ${first.membre.codeMembre} : ${first.ok ? 'erreur' : (first.message ?? 'erreur')}.`
        );
      });
  }

  private executerAccorderEmprunt(tipo: EmpruntTypeUi, m: number): void {
    this.enregistrement.set(true);
    this.empruntService
      .accorder(this.orgId, {
        membreId: this.form.controls.membreId.value!,
        typeEmprunt: this.typeEmpruntApi(tipo),
        montant: m,
        nbEcheances: tipo === 'etale' ? Number(this.form.controls.nbEcheances.value) : undefined,
        dateOctroi: this.form.controls.dateOctroi.value,
        observation: this.form.controls.motif.value?.trim() || undefined,
      })
      .subscribe({
        next: (created) => {
          this.enregistrement.set(false);
          const avance = Number(created.montantAvanceCaisse ?? 0);
          if (avance > 0) {
            this.showToast(
              `✅ Emprunt accordé. Avance Caisse tracée : ${formatFcfa(avance)} (à rembourser en priorité à la Caisse).`
            );
          } else {
            this.showToast('✅ Emprunt accordé avec succès.');
          }
          this.empruntService.lister(this.orgId).subscribe({
            next: (list) => this.emprunts.set(list),
            error: () => this.emprunts.set([]),
          });
          if (this.typeUi() === 'historique') {
            this.chargerHistorique();
          }
          this.annuler();
        },
        error: (err: HttpErrorResponse) => {
          this.enregistrement.set(false);
          const msg =
            typeof err.error === 'object' && err.error && 'message' in err.error
              ? String((err.error as { message: string }).message)
              : 'Impossible d\'accorder l\'emprunt.';
          this.showToast(msg);
        },
      });
  }

  ouvrirConfirmDialog(view: ConfirmDialogView, onConfirm: () => void): void {
    this.confirmCallback = onConfirm;
    this.confirmDialog.set(view);
  }

  fermerConfirmDialog(): void {
    this.confirmDialog.set(null);
    this.confirmCallback = null;
  }

  onConfirmDialogOk(): void {
    const cb = this.confirmCallback;
    this.fermerConfirmDialog();
    cb?.();
  }

  onConfirmDialogCancel(): void {
    this.fermerConfirmDialog();
  }

  private typeEmpruntApi(tipo: EmpruntTypeUi): TypeEmprunt {
    switch (tipo) {
      case 'caisse':
        return 'CAISSE';
      case 'sol':
        return 'SOLIDARITE';
      default:
        return 'ETALE';
    }
  }

  /** Type d'emprunt correspondant à l'onglet d'octroi actif ; null sur l'onglet historique. */
  private typeEmpruntPourOctroiCourant(): TypeEmprunt | null {
    const t = this.typeUi();
    if (t === 'historique') return null;
    return this.typeEmpruntApi(t);
  }

  private showToast(msg: string): void {
    this.notify.show(msg);
  }

  private formatDateEcheanceLabel(iso: string): string {
    if (!iso) return '—';
    const d = new Date(iso + 'T12:00:00');
    if (Number.isNaN(d.getTime())) return '—';
    return new Intl.DateTimeFormat('fr-FR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    }).format(d);
  }

  private todayIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private addMonthsLabel(isoDate: string, add: number): string {
    const d = new Date(isoDate + 'T12:00:00');
    d.setMonth(d.getMonth() + add);
    return new Intl.DateTimeFormat('fr-FR', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    }).format(d);
  }
}
