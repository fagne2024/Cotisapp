import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, Subscription } from 'rxjs';
import { FilterQueryNav, qpEnum } from '../../shared/util/filter-query.util';
import { AuthService } from '../../core/services/auth.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import {
  CompteMembreDto,
  MembreService,
  MembreDto,
  OperationMembreDto,
} from '../../core/services/membre.service';
import { SuiviMensuelDto, SuiviMensuelService } from '../../core/services/suivi-mensuel.service';
import { environment } from '../../../environments/environment';
import { EmpruntService, EmpruntDto, EcheanceDto } from '../../core/services/emprunt.service';
import { postePourMembre } from './membres-poste.util';
import { membreDemoSiApiAbsente } from './membres-demo.util';
import { MembreSoldeMembreDto } from '../../core/services/membre.service';
import { MembreRecapJourneeComponent } from './membre-recap-journee.component';
import { DROIT_ACTION_IMPORTS } from '../../shared/imports/droit-action.imports';
import { MonCompteOperationsComponent } from './mon-compte-operations.component';
import { downloadCsv } from '../../shared/util/csv-download.util';
import {
  alerteCotisationManquee,
  buildCarteEmprunts,
  buildComptesCartes,
  formatMontantCompte,
  formatMontantSolde,
  calculerSoldeMembreLocal,
  classeCompte,
  HistFiltreType,
  HistOpRow,
  iconeCompte,
  filtrePourOperation,
  libelleOperation,
  operationVersLigne,
  resumeMoisLignes,
  semainesCotisationMois,
  soldeComptes,
} from './membre-fiche.util';

const AV_COLORS = ['#7c3aed', '#1e6fa8', '#1a5c3a', '#c9922a', '#c0392b', '#2d7a52'];

@Component({
  selector: 'app-membre-fiche',
  standalone: true,
  imports: [RouterLink, MembreRecapJourneeComponent, MonCompteOperationsComponent, ...DROIT_ACTION_IMPORTS],
  templateUrl: './membre-fiche.component.html',
  styleUrl: './membre-fiche.component.scss',
})
export class MembreFicheComponent implements OnInit, OnDestroy {
  readonly Math = Math;
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly queryNav = new FilterQueryNav();
  private readonly queryDefaults = { hist: 'tous' };
  private sub = new Subscription();
  private readonly membreService = inject(MembreService);
  private readonly empruntService = inject(EmpruntService);
  private readonly suiviService = inject(SuiviMensuelService);

  readonly membre = signal<MembreDto | null>(null);
  readonly comptes = signal<CompteMembreDto[]>([]);
  readonly operations = signal<OperationMembreDto[]>([]);
  readonly suiviMois = signal<SuiviMensuelDto | null>(null);
  readonly emprunts = signal<EmpruntDto[]>([]);
  readonly soldeMembre = signal<MembreSoldeMembreDto | null>(null);
  readonly loadError = signal(false);
  readonly loading = signal(true);

  readonly filtreHistType = signal<HistFiltreType | 'tous'>('tous');

  readonly vueMonCompte = computed(
    () => this.auth.currentRole() === 'MEMBRE' || this.router.url.includes('/mon-compte')
  );

  readonly poste = computed(() => {
    const m = this.membre();
    return m ? postePourMembre(m.codeMembre, m.poste) : null;
  });

  readonly heroAvColor = computed(() => {
    const m = this.membre();
    if (!m) return AV_COLORS[0];
    return AV_COLORS[m.id % AV_COLORS.length];
  });

  readonly initials = computed(() => {
    const m = this.membre();
    if (!m) return '';
    return m.nomComplet
      .split(' ')
      .filter(Boolean)
      .map((p) => p[0])
      .join('')
      .slice(0, 2)
      .toUpperCase();
  });

  readonly depuis = computed(() => {
    const label = this.formatMoisLong(this.membre()?.dateAdhesion ?? this.membre()?.dateCreation);
    return label === '—' ? '—' : 'Depuis ' + label;
  });

  readonly adhesionCourt = computed(() => this.formatMoisLong(this.membre()?.dateAdhesion ?? this.membre()?.dateCreation));

  readonly epargneHebdo = computed(() => soldeComptes(this.comptes(), ['EPARGNE_HEBDO']));

  readonly epargneMois = computed(() => soldeComptes(this.comptes(), ['EPARGNE_MOIS']));

  /** Somme épargne hebdo + épargne mois (alignée sur les cartes comptes). */
  readonly epargne = computed(() => this.epargneHebdo() + this.epargneMois());

  readonly solidarite = computed(() => soldeComptes(this.comptes(), ['SOLIDARITE']));

  readonly operationsRows = computed(() => this.operations().map((op) => operationVersLigne(op)));

  readonly operationsFiltres = computed(() => {
    const t = this.filtreHistType();
    const rows = this.operationsRows();
    if (t === 'tous') return rows;
    return rows.filter((op) => op.type === t);
  });

  readonly cotisationsMoisCourant = computed(() => {
    const mois = this.moisCourant();
    return this.operations().filter(
      (o) =>
        o.typeOperation === 'COTISATION' &&
        (o.moisAnnee === mois || (o.dateOperation?.startsWith(mois) ?? false))
    ).length;
  });

  readonly cotisationMoisMontant = computed(() => {
    const mois = this.moisCourant();
    const op = this.operations().find((o) => o.typeOperation === 'COTISATION_MOIS' && o.moisAnnee === mois);
    return op ? Number(op.montant) : this.suiviMois()?.montantPaye ?? 0;
  });

  readonly moisCourantLabel = computed(() => {
    const [y, m] = this.moisCourant().split('-').map(Number);
    const d = new Date(y, (m ?? 1) - 1, 1);
    return new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' }).format(d);
  });

  readonly empruntsEnCours = computed(() => {
    const m = this.membre();
    if (!m) return [];
    return this.emprunts().filter((e) => e.membreId === m.id && e.statut === 'EN_COURS');
  });

  readonly montantEmpruntsEnCours = computed(() =>
    this.empruntsEnCours().reduce(
      (s, e) => s + Math.max(0, Number(e.montantTotal) - Number(e.montantRembourse)),
      0
    )
  );

  readonly formatMontantSolde = formatMontantSolde;
  readonly formatMontantCompte = formatMontantCompte;

  readonly comptesCartes = computed(() => [
    ...buildComptesCartes(this.comptes(), this.operations(), this.emprunts()),
    buildCarteEmprunts(this.empruntsEnCours()),
  ]);

  readonly semainesCotis = computed(() =>
    semainesCotisationMois(this.operations(), this.moisCourant())
  );

  readonly alerteCotis = computed(() =>
    alerteCotisationManquee(this.operations(), this.moisCourant())
  );

  readonly resumeMois = computed(() =>
    resumeMoisLignes(this.operations(), this.moisCourant(), this.epargne())
  );

  readonly semainesPayees = computed(() =>
    this.semainesCotis().filter((s) => s.statut === 'ok').length
  );

  readonly semainesTotal = computed(() =>
    this.semainesCotis().filter((s) => s.statut !== 'future').length
  );

  readonly participationPct = computed(() => {
    const total = this.semainesTotal();
    if (!total) return 0;
    return Math.round((this.semainesPayees() / total) * 100);
  });

  readonly tendanceEpargne = computed(() => {
    const mois = this.moisCourant();
    const hebdo = this.operations().filter(
      (o) =>
        o.typeOperation === 'COTISATION' &&
        (o.moisAnnee === mois || (o.dateOperation?.startsWith(mois) ?? false))
    );
    const total = hebdo.reduce((s, o) => s + Number(o.montant), 0);
    if (total <= 0) return null;
    return `↑ +${this.formatFcfa(total)} ce mois`;
  });

  readonly totalEmpruntsHistorique = computed(() => {
    const m = this.membre();
    if (!m) return 0;
    return this.emprunts().filter((e) => e.membreId === m.id).length;
  });

  readonly iconeCompte = iconeCompte;
  readonly classeCompte = classeCompte;

  @HostListener('window:keydown', ['$event'])
  handleKeyboardEvent(event: KeyboardEvent): void {
    // Ctrl+E: Export member data
    if (event.ctrlKey && event.key.toLowerCase() === 'e') {
      event.preventDefault();
      this.exportMemberData();
    }
    // Ctrl+Shift+E: Export operations
    if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() === 'e') {
      event.preventDefault();
      this.exportOperations();
    }
    // Escape: Navigate back
    if (event.key === 'Escape' && !this.vueMonCompte()) {
      event.preventDefault();
      this.goBack();
    }
  }

  ngOnInit(): void {
    this.sub.add(
      this.route.queryParamMap.subscribe((pm) => {
        this.queryNav.runSync(() => {
          this.filtreHistType.set(
            qpEnum(pm, 'hist', ['tous', 'cotis', 'mois', 'remb', 'pen'] as const, 'tous')
          );
        });
      })
    );

    this.sub.add(
      this.route.paramMap.subscribe(() => {
        const orgId = organisationCouranteId(this.route, this.auth);
        if (orgId == null) {
          this.loading.set(false);
          this.loadError.set(true);
          return;
        }
        if (this.vueMonCompte()) {
          this.chargerMonCompte(orgId);
          return;
        }
        const id = this.resoudreMembreId();
        if (id == null) {
          this.loadError.set(true);
          this.loading.set(false);
          return;
        }
        this.loading.set(true);
        this.membreService.get(orgId, id).subscribe({
          next: (mem) => {
            this.membre.set(mem);
            this.loadError.set(false);
            this.chargerDonneesFiche(orgId, id);
          },
          error: () => {
            if (!environment.production) {
              this.membre.set(membreDemoSiApiAbsente(id));
              this.loadError.set(false);
              this.comptes.set([]);
              this.operations.set([]);
              this.emprunts.set([]);
            } else {
              this.membre.set(null);
              this.loadError.set(true);
            }
            this.loading.set(false);
          },
        });
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.queryNav.destroy();
  }

  private chargerMonCompte(orgId: number): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.membreService.chargerMonCompte(orgId, this.moisCourant()).subscribe({
      next: (fiche) => {
        this.membre.set(fiche.membre);
        this.comptes.set(fiche.comptes);
        this.operations.set(fiche.operations);
        this.suiviMois.set(fiche.suiviMensuel);
        this.emprunts.set(fiche.emprunts);
        this.soldeMembre.set(fiche.solde);
        this.loadError.set(false);
        this.loading.set(false);
      },
      error: () => {
        this.membre.set(null);
        this.comptes.set([]);
        this.operations.set([]);
        this.suiviMois.set(null);
        this.emprunts.set([]);
        this.soldeMembre.set(null);
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  private chargerDonneesFiche(orgId: number, membreId: number): void {
    const mois = this.moisCourant();
    forkJoin({
      comptes: this.membreService.listerComptes(orgId, membreId),
      operations: this.membreService.listerOperations(orgId, membreId),
      suivi: this.suiviService.lister(orgId, mois),
      emprunts: this.empruntService.lister(orgId),
      solde: this.membreService.obtenirSoldeMembre(orgId, membreId),
    }).subscribe({
      next: ({ comptes, operations, suivi, emprunts, solde }) => {
        this.comptes.set(comptes);
        this.operations.set(operations);
        this.suiviMois.set(suivi.find((s) => s.membreId === membreId) ?? null);
        this.emprunts.set(emprunts);
        this.soldeMembre.set(solde);
        this.loading.set(false);
      },
      error: () => {
        this.comptes.set([]);
        this.operations.set([]);
        this.suiviMois.set(null);
        this.soldeMembre.set(null);
        forkJoin({
          emprunts: this.empruntService.lister(orgId),
          comptes: this.membreService.listerComptes(orgId, membreId),
          operations: this.membreService.listerOperations(orgId, membreId),
        }).subscribe({
          next: ({ emprunts, comptes, operations }) => {
            this.emprunts.set(emprunts);
            this.comptes.set(comptes);
            this.operations.set(operations);
            this.soldeMembre.set(calculerSoldeMembreLocal(membreId, comptes, emprunts, operations));
          },
          error: () => this.emprunts.set([]),
        });
        this.loading.set(false);
      },
    });
  }

  private moisCourant(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  }

  private formatMoisLong(iso?: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '—';
    return new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' }).format(d);
  }

  formatFcfa(n: number): string {
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(n);
  }

  numFcfa(n: number): string {
    return this.formatFcfa(n) + ' F';
  }

  soldeCompte(c: CompteMembreDto): string {
    return this.numFcfa(Number(c.solde ?? 0));
  }

  typeLabel(t: string): string {
    const m: Record<string, string> = { ETALE: 'Étalé', SOLIDARITE: 'Solidarité', CAISSE: 'Caisse' };
    return m[t] ?? t;
  }

  progressPct(emp: EmpruntDto): number {
    if (!emp.montantTotal) return 0;
    return Math.min(100, Math.round((emp.montantRembourse / emp.montantTotal) * 100));
  }

  empruntRetard(emp: EmpruntDto): boolean {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return (emp.echeances ?? []).some((ech: EcheanceDto) => {
      if (ech.statut === 'PAYE') return false;
      const d = new Date(ech.dateEcheance);
      d.setHours(0, 0, 0, 0);
      return d < today;
    });
  }

  categorieBureau(): string {
    const p = this.poste();
    if (!p || p.kind === 'simple') return 'Membre simple';
    return 'Membre du bureau';
  }

  statutSuiviLabel(): string {
    const s = this.suiviMois();
    if (!s) return 'Non généré';
    switch (s.statut) {
      case 'PAYE':
        return 'Payé';
      case 'PARTIEL':
        return 'Partiel';
      default:
        return 'Non payé';
    }
  }

  onFiltreHistType(ev: Event): void {
    const v = (ev.target as HTMLSelectElement).value;
    const allowed: (HistFiltreType | 'tous')[] = ['tous', 'cotis', 'mois', 'remb', 'pen'];
    this.filtreHistType.set(allowed.includes(v as HistFiltreType | 'tous') ? (v as HistFiltreType | 'tous') : 'tous');
    this.queryNav.push(this.router, this.route, { hist: this.filtreHistType() }, this.queryDefaults);
  }

  orgCourante(): number | null {
    return organisationCouranteId(this.route, this.auth);
  }

  statutDotClass(statut: string): string {
    switch (statut) {
      case 'ok':
        return 'cd-ok';
      case 'miss':
        return 'cd-miss';
      case 'mois':
        return 'cd-mois';
      default:
        return 'cd-future';
    }
  }

  statutDotChar(statut: string): string {
    switch (statut) {
      case 'ok':
        return '✓';
      case 'miss':
        return '✗';
      case 'mois':
        return 'M';
      default:
        return '·';
    }
  }

  private resoudreMembreId(): number | null {
    if (this.vueMonCompte()) {
      const mid = this.auth.currentMembreId();
      return mid != null ? mid : null;
    }
    const mid = this.route.snapshot.paramMap.get('membreId');
    if (!mid) return null;
    const id = Number(mid);
    return Number.isFinite(id) ? id : null;
  }

  exportMemberData(): void {
    const m = this.membre();
    if (!m) return;

    const rows: (string | number)[][] = [
      ['PROFIL MEMBRE', ''],
      ['Nom complet', m.nomComplet],
      ['Code membre', m.codeMembre],
      ['Email', m.email || '—'],
      ['Téléphone', m.telephone || '—'],
      ['Catégorie', this.categorieBureau()],
      ['Statut', m.actif ? 'Actif' : 'Inactif'],
      ['Adhésion', this.adhesionCourt()],
      ['', ''],
      ['SOLDES ACTUELS', ''],
      ['Épargne totale', this.numFcfa(this.epargne())],
      ['Solidarité', this.numFcfa(this.solidarite())],
      ['Emprunt en cours', this.numFcfa(this.montantEmpruntsEnCours())],
      ['', ''],
      ['PARTICIPATION COURANT', ''],
      ['Semaines à jour', `${this.semainesPayees()} / ${this.semainesTotal()}`],
      ['Taux participation', `${this.participationPct()}%`],
      ['Mois courant', this.moisCourantLabel()],
    ];

    const filename = `Fiche_${m.codeMembre}_${m.nomComplet.replace(/\s+/g, '_')}.csv`;
    downloadCsv(filename, ['Champ', 'Valeur'], rows);
  }

  exportOperations(): void {
    const m = this.membre();
    const ops = this.operations();
    if (!m || ops.length === 0) return;

    const t = this.filtreHistType();
    const filtered =
      t === 'tous' ? ops : ops.filter((o) => filtrePourOperation(o.typeOperation) === t);
    if (filtered.length === 0) return;

    const dataRows = filtered.map((op) => {
      const total = Number(op.montant) + Number(op.montantFrais ?? 0);
      return [
        libelleOperation(op.typeOperation),
        total,
        op.moisAnnee ?? '—',
        op.dateOperation ?? '—',
        op.observation ?? '—',
      ];
    });

    const filename = `Operations_${m.codeMembre}_${new Date().toISOString().split('T')[0]}.csv`;
    downloadCsv(filename, ['Type', 'Montant (FCFA)', 'Mois', 'Date', 'Observation'], dataRows);
  }

  goBack(): void {
    const orgId = this.orgCourante();
    if (orgId) {
      this.router.navigate(['/organisations', orgId, 'membres']);
    }
  }

}
