import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  EMPTY,
  Subject,
  Subscription,
  switchMap,
  tap,
} from 'rxjs';
import {
  CompteMembreResumeDto,
  CompteOrgCardDto,
  CompteReleveDto,
  CompteReleveService,
  CompteReleveSyntheseDto,
  ReleveQuery,
} from '../../core/services/compte-releve.service';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import { formatFcfa } from '../../core/utils/currency.util';
import { appliquerFiltresReleve, paginerReleveGroupes } from './comptes-releves.util';
import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import { paginateSlice } from '../../shared/util/pagination.util';
import { DROIT_ACTION_IMPORTS } from '../../shared/imports/droit-action.imports';

type PanelTab = 'org' | 'mbr';

function n(v: number | string | null | undefined): number {
  if (v == null || v === '') return 0;
  return typeof v === 'number' ? v : parseFloat(String(v));
}

function scopeForTypeCompte(typeCompte: string): string {
  const t = (typeCompte || '').toUpperCase();
  if (t === 'BANQUE') return 'banque';
  if (t === 'SOLIDARITE') return 'sol';
  if (t === 'INTERET') return 'interet';
  if (t === 'AMENDES' || t === 'AMENDES_AGGREGAT') return 'amendes';
  return 'caisse';
}

function isMembreSimple(posteLabel: string): boolean {
  return posteLabel === 'Membre simple' || posteLabel === 'Membre';
}

function defaultDateDebut(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
}

function defaultDateFin(): string {
  return new Date().toISOString().slice(0, 10);
}

interface ReleveLoadParams {
  scope: string;
  compteId: number | null;
  membreId: number | null;
  dateDebut: string;
  dateFin: string;
}

@Component({
  selector: 'app-comptes-releves',
  standalone: true,
  imports: [RouterLink, ListPaginationComponent, ...DROIT_ACTION_IMPORTS],
  templateUrl: './comptes-releves.component.html',
  styleUrls: ['./comptes-releves.component.scss', '../../shared/styles/pagination.scss'],
})
export class ComptesRelevesComponent implements OnInit, OnDestroy {
  readonly Math = Math;
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly compteReleveService = inject(CompteReleveService);
  private readonly notify = inject(NotificationService);

  private orgId: number | null = null;
  private subs = new Subscription();
  private readonly releveLoad$ = new Subject<ReleveLoadParams>();

  readonly formatFcfa = formatFcfa;
  readonly n = n;

  readonly panelTab = signal<PanelTab>('org');
  readonly loadingSynthese = signal(true);
  readonly loadingReleve = signal(false);
  readonly synthese = signal<CompteReleveSyntheseDto | null>(null);
  /** Relevé brut (période + compte), sans filtres UI. */
  readonly releveBrut = signal<CompteReleveDto | null>(null);

  readonly selectedScope = signal('caisse');
  readonly selectedCompteId = signal<number | null>(null);
  readonly selectedMembreId = signal<number | null>(null);

  readonly filtreType = signal('');
  readonly filtreStatut = signal('');
  readonly dateDebut = signal(defaultDateDebut());
  readonly dateFin = signal(defaultDateFin());
  readonly recherche = signal('');
  readonly rechercheMembre = signal('');

  readonly pageReleve = signal(1);
  readonly pageMembresBureau = signal(1);
  readonly pageMembresSimples = signal(1);
  readonly pageSizeReleve = 20;
  readonly pageSizeMembres = 12;

  readonly vueMembre = computed(() => this.panelTab() === 'mbr');

  readonly encoursEmpruntsMontant = computed(() => n(this.synthese()?.encoursEmprunts));
  readonly nbEmpruntsEnCours = computed(() => this.synthese()?.nbEmpruntsEnCours ?? 0);

  /** Relevé affiché = brut + filtres instantanés (sans API). */
  readonly releve = computed(() => {
    const brut = this.releveBrut();
    if (!brut) return null;
    return appliquerFiltresReleve(
      brut,
      this.filtreType(),
      this.filtreStatut(),
      this.recherche()
    );
  });

  readonly loading = computed(() => this.loadingSynthese() && !this.synthese());

  readonly membresBureau = computed(() => {
    const list = this.synthese()?.membres ?? [];
    return list.filter((m) => !isMembreSimple(m.posteLabel));
  });

  readonly membresSimples = computed(() => {
    const list = this.synthese()?.membres ?? [];
    return list.filter((m) => isMembreSimple(m.posteLabel));
  });

  readonly membresFiltresBureau = computed(() => this.filtrerMembresList(this.membresBureau()));
  readonly membresFiltresSimples = computed(() => this.filtrerMembresList(this.membresSimples()));

  readonly membresBureauPaged = computed(() =>
    paginateSlice(this.membresFiltresBureau(), this.pageMembresBureau(), this.pageSizeMembres)
  );

  readonly membresSimplesPaged = computed(() =>
    paginateSlice(this.membresFiltresSimples(), this.pageMembresSimples(), this.pageSizeMembres)
  );

  readonly releveLignesTotal = computed(() => {
    const r = this.releve();
    if (!r) return 0;
    return r.groupes.reduce((acc, g) => acc + g.lignes.length, 0);
  });

  readonly releveGroupesPaged = computed(() => {
    const r = this.releve();
    if (!r) return [];
    return paginerReleveGroupes(r.groupes, this.pageReleve(), this.pageSizeReleve).groupes;
  });

  @HostListener('window:keydown', ['$event'])
  handleKeyboardEvent(event: KeyboardEvent): void {
    // Ctrl+P: Print
    if (event.ctrlKey && event.key.toLowerCase() === 'p') {
      event.preventDefault();
      this.imprimer();
    }
    // Ctrl+E: Export PDF
    if (event.ctrlKey && event.key.toLowerCase() === 'e') {
      event.preventDefault();
      this.exportPdf();
    }
    // Ctrl+Shift+E: Export Excel
    if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() === 'e') {
      event.preventDefault();
      this.exportExcel();
    }
  }

  ngOnInit(): void {
    this.orgId = organisationCouranteId(this.route, this.auth);
    if (this.orgId == null) {
      this.loadingSynthese.set(false);
      return;
    }

    this.subs.add(
      this.releveLoad$
        .pipe(
          debounceTime(280),
          distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
          tap(() => this.loadingReleve.set(true)),
          switchMap((params) => {
            if (this.orgId == null) return EMPTY;
            return this.compteReleveService.chargerReleve(this.orgId, this.toReleveQuery(params)).pipe(
              catchError((err) => {
                this.loadingReleve.set(false);
                this.notify.info(err?.error?.message ?? 'Impossible de charger le relevé');
                return EMPTY;
              })
            );
          })
        )
        .subscribe((r) => {
          this.releveBrut.set(r);
          this.pageReleve.set(1);
          this.loadingReleve.set(false);
        })
    );

    this.chargerSynthese(true);
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  setPanelTab(tab: PanelTab): void {
    this.panelTab.set(tab);
    if (tab === 'mbr') {
      const membres = this.synthese()?.membres ?? [];
      if (membres.length && this.selectedMembreId() == null) {
        this.selectMembre(membres[0]);
      } else {
        this.planifierChargementReleve();
      }
    } else {
      const cards = this.synthese()?.comptesOrganisation ?? [];
      if (cards.length) {
        const match = cards.find((c) => scopeForTypeCompte(c.typeCompte) === this.selectedScope());
        this.selectCompteOrg(match ?? cards[0]);
      } else {
        this.planifierChargementReleve();
      }
    }
  }

  selectCompteOrg(card: CompteOrgCardDto): void {
    this.panelTab.set('org');
    this.selectedScope.set(scopeForTypeCompte(card.typeCompte));
    this.selectedCompteId.set(card.compteId);
    this.selectedMembreId.set(null);
    this.pageReleve.set(1);
    this.planifierChargementReleve();
  }

  selectMembre(m: CompteMembreResumeDto): void {
    this.panelTab.set('mbr');
    this.selectedMembreId.set(m.membreId);
    this.selectedScope.set('membre');
    this.selectedCompteId.set(null);
    this.pageReleve.set(1);
    this.planifierChargementReleve();
  }

  isCompteOrgActif(card: CompteOrgCardDto): boolean {
    return this.panelTab() === 'org' && this.selectedCompteId() === card.compteId;
  }

  isMembreActif(m: CompteMembreResumeDto): boolean {
    return this.panelTab() === 'mbr' && this.selectedMembreId() === m.membreId;
  }

  compteBtnClass(card: CompteOrgCardDto): string {
    const base = 'compte-btn';
    if (!this.isCompteOrgActif(card)) return base;
    const t = (card.typeCompte || '').toUpperCase();
    if (t === 'BANQUE') return `${base} active-bl`;
    if (t === 'SOLIDARITE') return `${base} active-or`;
    if (t === 'INTERET') return `${base} active-pu`;
    if (t === 'AMENDES' || t === 'AMENDES_AGGREGAT') return `${base} active-re`;
    return `${base} active`;
  }

  soldeCouleur(card: CompteOrgCardDto): string {
    const t = (card.typeCompte || '').toUpperCase();
    if (t === 'BANQUE') return 'var(--bl)';
    if (t === 'SOLIDARITE') return 'var(--or)';
    if (t === 'INTERET') return 'var(--pu)';
    if (t === 'AMENDES' || t === 'AMENDES_AGGREGAT') return 'var(--re)';
    return 'var(--g1)';
  }

  iconeFondOrg(card: CompteOrgCardDto): string {
    const t = (card.typeCompte || '').toUpperCase();
    if (t === 'BANQUE') return 'var(--bl2)';
    if (t === 'SOLIDARITE') return 'var(--or3)';
    if (t === 'INTERET') return 'var(--pu2)';
    if (t === 'AMENDES' || t === 'AMENDES_AGGREGAT') return 'var(--re2)';
    return 'var(--g3)';
  }

  trendClass(v: number): string {
    if (v > 0) return 'tr-up';
    if (v < 0) return 'tr-dn';
    return 'tr-zero';
  }

  trendLabel(v: number): string {
    if (v > 0) return `↑ +${formatFcfa(v).replace(' F', '')} auj.`;
    if (v < 0) return `↓ ${formatFcfa(v)} auj.`;
    return '= 0 auj.';
  }

  variationJourLabel(v: number): string {
    if (v > 0) return `↑ +${formatFcfa(v)} aujourd'hui`;
    if (v < 0) return `↓ ${formatFcfa(v)} aujourd'hui`;
    return "= 0 F aujourd'hui";
  }

  montantLigne(sens: string, montant: number, annulee: boolean): string {
    if (annulee) return formatFcfa(montant);
    const m = n(montant);
    if (sens === 'credit') return `+${formatFcfa(m)}`;
    return `−${formatFcfa(m)}`;
  }

  montantClass(sens: string, annulee: boolean): string {
    if (annulee) return '';
    return sens === 'credit' ? 'cr-c' : 'db-c';
  }

  /** Filtres UI : pas d’appel réseau. */
  onFiltresChange(): void {
    /* no-op : releve() computed se met à jour */
  }

  onRechercheInput(value: string): void {
    this.recherche.set(value);
    this.pageReleve.set(1);
  }

  onRechercheMembreInput(value: string): void {
    this.rechercheMembre.set(value);
    this.pageMembresBureau.set(1);
    this.pageMembresSimples.set(1);
  }

  onFiltreTypeChange(value: string): void {
    this.filtreType.set(value);
    this.pageReleve.set(1);
  }

  onFiltreStatutChange(value: string): void {
    this.filtreStatut.set(value);
    this.pageReleve.set(1);
  }

  onDatesChange(): void {
    this.pageReleve.set(1);
    this.planifierChargementReleve();
  }

  exportPdf(): void {
    this.notify.info('Export PDF — fonctionnalité en développement (Ctrl+E)');
  }

  exportExcel(): void {
    this.notify.info('Export Excel — fonctionnalité en développement (Ctrl+Shift+E)');
  }

  imprimer(): void {
    this.notify.info('Impression… (Ctrl+P)');
    window.print();
  }

  private filtrerMembresList(list: CompteMembreResumeDto[]): CompteMembreResumeDto[] {
    const q = this.rechercheMembre().trim().toLowerCase();
    if (!q) return list;
    return list.filter(
      (m) =>
        m.nomComplet.toLowerCase().includes(q) ||
        m.codeMembre.toLowerCase().includes(q) ||
        m.posteLabel.toLowerCase().includes(q)
    );
  }

  private chargerSynthese(chargerReleveInitial: boolean): void {
    if (this.orgId == null) return;
    this.loadingSynthese.set(true);
    this.compteReleveService.chargerSynthese(this.orgId).subscribe({
      next: (synthese) => {
        this.synthese.set(synthese);
        this.loadingSynthese.set(false);
        if (synthese.comptesOrganisation.length && this.selectedCompteId() == null) {
          const first = synthese.comptesOrganisation[0];
          this.selectedCompteId.set(first.compteId);
          this.selectedScope.set(scopeForTypeCompte(first.typeCompte));
        }
        if (chargerReleveInitial) {
          this.planifierChargementReleve();
        }
      },
      error: (err) => {
        this.loadingSynthese.set(false);
        this.notify.info(err?.error?.message ?? 'Impossible de charger les comptes');
      },
    });
  }

  private planifierChargementReleve(): void {
    if (this.orgId == null) return;
    if (this.panelTab() === 'mbr' && this.selectedMembreId() == null) return;
    if (this.panelTab() === 'org' && this.selectedCompteId() == null) return;

    this.releveLoad$.next({
      scope: this.panelTab() === 'mbr' ? 'membre' : this.selectedScope(),
      compteId: this.panelTab() === 'org' ? this.selectedCompteId() : null,
      membreId: this.panelTab() === 'mbr' ? this.selectedMembreId() : null,
      dateDebut: this.dateDebut(),
      dateFin: this.dateFin(),
    });
  }

  private toReleveQuery(params: ReleveLoadParams): ReleveQuery {
    const q: ReleveQuery = {
      scope: params.scope,
      dateDebut: params.dateDebut,
      dateFin: params.dateFin,
    };
    if (params.membreId != null) q.membreId = params.membreId;
    else if (params.compteId != null) q.compteId = params.compteId;
    return q;
  }

  lienEmpruntsSuivi(): (string | number)[] {
    if (this.orgId == null) {
      return ['/organisations', 0, 'operations', 'emprunts', 'suivi'];
    }
    return ['/organisations', this.orgId, 'operations', 'emprunts', 'suivi'];
  }
}
