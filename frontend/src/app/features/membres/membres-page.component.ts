import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, Subscription } from 'rxjs';
import { FilterQueryNav, qpEnum, qpString } from '../../shared/util/filter-query.util';
import { AuthService } from '../../core/services/auth.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import {
  CompteModeleMembreDto,
  CompteModeleMembreService,
} from '../../core/services/compte-modele-membre.service';
import {
  ImportMembresResult,
  MembreService,
  MembreDto,
} from '../../core/services/membre.service';
import { EmpruntService, EmpruntDto, EcheanceDto } from '../../core/services/emprunt.service';
import {
  PosteKind,
  posteKindVersApi,
  postePourMembre,
  PosteMeta,
} from './membres-poste.util';
import { mapSoldesParMembre, SoldesMembreLigne, SOLDES_VIDES } from './membres-soldes.util';
import { downloadCsv } from '../../shared/util/csv-download.util';
import { formatFcfa } from '../../core/utils/currency.util';

export interface MembreRow {
  raw: MembreDto;
  poste: PosteMeta;
  initials: string;
  avColor: string;
  adhesion: string;
  soldes: SoldesMembreLigne;
  empruntMontant: number | null;
  empruntRetard: boolean;
}

const AV_COLORS = ['#7c3aed', '#1e6fa8', '#1a5c3a', '#c9922a', '#c0392b', '#2d7a52'];

import { HighlightPipe } from '../../shared/pipes/highlight.pipe';
import { DROIT_ACTION_IMPORTS } from '../../shared/imports/droit-action.imports';

@Component({
  selector: 'app-membres-page',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, HighlightPipe, ...DROIT_ACTION_IMPORTS],
  templateUrl: './membres-page.component.html',
  styleUrls: ['./membres-page.component.scss', '../../shared/styles/action-grisee.scss'],
})
export class MembresPageComponent implements OnInit, OnDestroy {
  readonly Math = Math; // Expose Math to template

  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly queryNav = new FilterQueryNav();
  private readonly queryDefaults = {
    tab: 'tous',
    poste: '',
    statut: '',
    q: '',
    vue: 'table',
  };
  private sub = new Subscription();
  private readonly fb = inject(FormBuilder);
  private readonly membreService = inject(MembreService);
  private readonly empruntService = inject(EmpruntService);
  private readonly compteModeleService = inject(CompteModeleMembreService);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly membres = signal<MembreDto[]>([]);
  readonly soldesParMembre = signal<Map<number, SoldesMembreLigne>>(new Map());
  readonly emprunts = signal<EmpruntDto[]>([]);

  readonly tab = signal<'tous' | 'bureau' | 'simples' | 'suspendus'>('tous');
  readonly posteSelect = signal('');
  readonly statutSelect = signal('');
  readonly searchInput = signal('');
  readonly viewMode = signal<'table' | 'cards'>('table');
  readonly page = signal(1);
  readonly pageSizeOptions = [10, 20, 50];
  readonly pageSize = signal(10);
  readonly selectedMembreIds = signal<Set<number>>(new Set());

  readonly modalOpen = signal(false);
  /** null = création, sinon id du membre en cours d’édition */
  readonly modalEditId = signal<number | null>(null);
  readonly modalLoading = signal(false);
  readonly modalSaving = signal(false);
  readonly modalError = signal<string | null>(null);

  /** Modèle CSV statique (toujours disponible, sans appel API). */
  readonly modeleImportUrl = 'assets/modele-import-membres.csv';

  readonly importModalOpen = signal(false);
  readonly importDownloading = signal(false);
  readonly importUploading = signal(false);
  readonly importFichier = signal<File | null>(null);
  readonly importResult = signal<ImportMembresResult | null>(null);
  readonly importError = signal<string | null>(null);
  readonly modelesCompte = signal<CompteModeleMembreDto[]>([]);
  readonly modelesCompteSelection = signal<Record<number, boolean>>({});
  readonly modalPoste = signal<PosteKind>('simple');

  readonly modalForm = this.fb.nonNullable.group({
    prenom: ['', Validators.required],
    nom: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    telephone: ['', Validators.required],
    dateAdhesion: [this.todayIso()],
    pieceIdentite: [''],
    epargneHebdo: [true],
    epargneMois: [true],
    solidarite: [true],
    penalite: [false],
    amende: [false],
    nouveauModeleCode: [''],
    nouveauModeleLibelle: [''],
    envoyerEmailActivation: [true],
    actif: [true],
    paiementMobileActif: [false],
  });

  readonly modalEnEdition = computed(() => this.modalEditId() != null);

  /** Activation mobile money « Mon compte » : réservée à l'admin GIE. */
  readonly peutConfigurerPaiementMobile = computed(
    () => this.auth.currentRole() === 'ADMIN_GIE' || this.auth.currentRole() === 'SUPERADMIN'
  );

  readonly deleteConfirmId = signal<number | null>(null);
  readonly deleteSaving = signal(false);
  readonly bulkMobileSaving = signal(false);

  ngOnInit(): void {
    const oid = organisationCouranteId(this.route, this.auth);
    if (oid == null) {
      this.loading.set(false);
      return;
    }

    this.sub.add(
      this.route.queryParamMap.subscribe((pm) => {
        this.queryNav.runSync(() => {
          this.tab.set(
            qpEnum(pm, 'tab', ['tous', 'bureau', 'simples', 'suspendus'] as const, 'tous')
          );
          this.posteSelect.set(qpString(pm, 'poste', 32));
          this.statutSelect.set(qpString(pm, 'statut', 16));
          this.searchInput.set(qpString(pm, 'q'));
          this.viewMode.set(qpEnum(pm, 'vue', ['table', 'cards'] as const, 'table'));
          this.page.set(1);
        });
      })
    );

    this.chargerListe(oid);
  }

  private chargerListe(oid: number): void {
    this.loading.set(true);
    this.loadError.set(null);
    forkJoin({
      membres: this.membreService.lister(oid, true),
      soldes: this.membreService.listerSoldesComptes(oid),
      emprunts: this.empruntService.lister(oid),
    }).subscribe({
      next: ({ membres, soldes, emprunts }) => {
        this.membres.set(membres);
        this.emprunts.set(emprunts);
        this.soldesParMembre.set(mapSoldesParMembre(soldes));
        this.loading.set(false);
      },
      error: (err) => {
        this.membres.set([]);
        this.emprunts.set([]);
        this.soldesParMembre.set(new Map());
        this.loading.set(false);
        const msg = err?.error?.message;
        this.loadError.set(typeof msg === 'string' ? msg : 'Impossible de charger les membres.');
      },
    });
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
        tab: this.tab(),
        poste: this.posteSelect(),
        statut: this.statutSelect(),
        q: this.searchInput(),
        vue: this.viewMode(),
      },
      this.queryDefaults,
      debounce ? 400 : 0
    );
  }

  private montantsParMembre(): Map<number, { total: number; retard: boolean }> {
    const map = new Map<number, { total: number; retard: boolean }>();
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    for (const emp of this.emprunts()) {
      if (emp.statut !== 'EN_COURS') continue;
      const prev = map.get(emp.membreId) ?? { total: 0, retard: false };
      prev.total += emp.montantRestant ?? 0;
      const late = (emp.echeances ?? []).some((ech: EcheanceDto) => {
        if (ech.statut === 'PAYE') return false;
        const d = new Date(ech.dateEcheance);
        d.setHours(0, 0, 0, 0);
        return d < today;
      });
      prev.retard = prev.retard || late;
      map.set(emp.membreId, prev);
    }
    return map;
  }

  readonly rowsAll = computed<MembreRow[]>(() => {
    const mp = this.montantsParMembre();
    const soldes = this.soldesParMembre();
    return this.membres().map((m) => {
      const poste = postePourMembre(m.codeMembre, m.poste);
      const emp = mp.get(m.id);
      const soldesLigne = soldes.get(m.id) ?? SOLDES_VIDES;
      return {
        raw: m,
        poste,
        initials: this.initialsFrom(m.nomComplet),
        avColor: AV_COLORS[m.id % AV_COLORS.length],
        adhesion: this.formatAdhesion(m.dateAdhesion ?? m.dateCreation),
        soldes: soldesLigne,
        empruntMontant: emp && emp.total > 0 ? emp.total : null,
        empruntRetard: emp?.retard ?? false,
      };
    });
  });

  readonly filteredRows = computed(() => {
    let list = this.rowsAll();
    const t = this.tab();
    if (t === 'bureau') list = list.filter((r) => r.poste.kind !== 'simple');
    else if (t === 'simples') list = list.filter((r) => r.poste.kind === 'simple');
    else if (t === 'suspendus') list = list.filter((r) => !r.raw.actif);

    const pk = this.posteSelect();
    if (pk) list = list.filter((r) => r.poste.kind === pk);

    const st = this.statutSelect();
    if (st === 'actif') list = list.filter((r) => r.raw.actif);
    else if (st === 'suspendu') list = list.filter((r) => !r.raw.actif);
    else if (st === 'exclu') list = [];

    const q = this.searchInput().trim().toLowerCase();
    if (q) {
      list = list.filter(
        (r) =>
          r.raw.nomComplet.toLowerCase().includes(q) ||
          r.raw.codeMembre.toLowerCase().includes(q) ||
          r.raw.nom.toLowerCase().includes(q) ||
          r.raw.prenom.toLowerCase().includes(q)
      );
    }
    return list;
  });

  readonly pagedRows = computed(() => {
    const p = this.page();
    const size = this.pageSize();
    const start = (p - 1) * size;
    return this.filteredRows().slice(start, start + size);
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filteredRows().length / this.pageSize())));

  readonly selectedCount = computed(() => this.selectedMembreIds().size);

  readonly allSelectedOnPage = computed(() => {
    const selected = this.selectedMembreIds();
    return this.pagedRows().length > 0 && this.pagedRows().every((r) => selected.has(r.raw.id));
  });

  readonly stats = computed(() => {
    const all = this.rowsAll();
    const actifs = all.filter((r) => r.raw.actif).length;
    const bureau = all.filter((r) => r.poste.kind !== 'simple').length;
    const susp = all.filter((r) => !r.raw.actif).length;
    const president = all.filter((r) => r.poste.kind === 'president').length;
    const secs = all.filter((r) => r.poste.kind === 'sg' || r.poste.kind === 'sga').length;
    const tres = all.filter((r) => r.poste.kind === 'tresorier').length;
    const sup = all.filter((r) => r.poste.kind === 'superviseur').length;
    const simple = all.filter((r) => r.poste.kind === 'simple').length;
    return { actifs, bureau, susp, president, secs, tres, sup, simple, total: all.length };
  });

  readonly bureauCards = computed(() =>
    this.rowsAll()
      .filter((r) => r.poste.kind !== 'simple')
      .slice(0, 5)
  );

  readonly tabCounts = computed(() => {
    const all = this.rowsAll();
    return {
      tous: all.length,
      bureau: all.filter((r) => r.poste.kind !== 'simple').length,
      simples: all.filter((r) => r.poste.kind === 'simple').length,
      suspendus: all.filter((r) => !r.raw.actif).length,
    };
  });

  readonly pageNumbers = computed(() => {
    const total = this.totalPages();
    const cur = this.page();
    if (total <= 5) return Array.from({ length: total }, (_, i) => i + 1);
    const set = new Set<number>([1, total, cur, cur - 1, cur + 1].filter((n) => n >= 1 && n <= total));
    return [...set].sort((a, b) => a - b);
  });

  readonly rangeLabel = computed(() => {
    const total = this.filteredRows().length;
    if (total === 0) return 'Aucun résultat';
    const p = this.page();
    const size = this.pageSize();
    const a = (p - 1) * size + 1;
    const b = Math.min(p * size, total);
    return `Affichage ${a}–${b} sur ${total} membre(s)`;
  });

  readonly phSubline = computed(() => {
    const s = this.stats();
    if (s.total === 0) {
      return 'Aucun membre — ajoutez ou importez des membres pour démarrer';
    }
    return `${s.actifs} membres actifs · ${s.bureau} membres du bureau · ${s.susp} suspendu(s)`;
  });

  setTab(id: 'tous' | 'bureau' | 'simples' | 'suspendus'): void {
    this.tab.set(id);
    this.page.set(1);
    this.pushFiltersToUrl();
  }

  onSearch(ev: Event): void {
    this.searchInput.set((ev.target as HTMLInputElement).value);
    this.page.set(1);
    this.pushFiltersToUrl(true);
  }

  onPosteFilter(ev: Event): void {
    this.posteSelect.set((ev.target as HTMLSelectElement).value);
    this.page.set(1);
    this.pushFiltersToUrl();
  }

  onStatutFilter(ev: Event): void {
    this.statutSelect.set((ev.target as HTMLSelectElement).value);
    this.page.set(1);
    this.pushFiltersToUrl();
  }

  setViewMode(m: 'table' | 'cards'): void {
    this.viewMode.set(m);
    this.pushFiltersToUrl();
  }

  goPage(p: number): void {
    const max = this.totalPages();
    this.page.set(Math.min(max, Math.max(1, p)));
  }

  onPageSizeChange(ev: Event): void {
    const value = (ev.target as HTMLSelectElement).value;
    this.changePageSize(+value);
  }

  changePageSize(newSize: number): void {
    this.pageSize.set(newSize);
    this.page.set(1);
    sessionStorage.setItem('membres_pageSize', String(newSize));
  }

  toggleMemberSelection(membreId: number): void {
    this.selectedMembreIds.update((set) => {
      const newSet = new Set(set);
      if (newSet.has(membreId)) {
        newSet.delete(membreId);
      } else {
        newSet.add(membreId);
      }
      return newSet;
    });
  }

  toggleAllOnPage(): void {
    if (this.allSelectedOnPage()) {
      // Deselect all on page
      this.selectedMembreIds.update((set) => {
        const newSet = new Set(set);
        this.pagedRows().forEach((r) => newSet.delete(r.raw.id));
        return newSet;
      });
    } else {
      // Select all on page
      this.selectedMembreIds.update((set) => {
        const newSet = new Set(set);
        this.pagedRows().forEach((r) => newSet.add(r.raw.id));
        return newSet;
      });
    }
  }

  isMemberSelected(membreId: number): boolean {
    return this.selectedMembreIds().has(membreId);
  }

  clearSelection(): void {
    this.selectedMembreIds.set(new Set());
  }

  bulkPaiementMobile(actif: boolean): void {
    const org = organisationCouranteId(this.route, this.auth);
    if (org == null || !this.peutConfigurerPaiementMobile()) return;
    const ids = [...this.selectedMembreIds()];
    if (ids.length === 0) return;
    const action = actif ? 'activer' : 'désactiver';
    if (!confirm(`${action.charAt(0).toUpperCase() + action.slice(1)} le mobile money pour ${ids.length} membre(s) ?`)) {
      return;
    }
    this.bulkMobileSaving.set(true);
    this.membreService.bulkPaiementMobile(org, ids, actif).subscribe({
      next: (res) => {
        this.bulkMobileSaving.set(false);
        this.clearSelection();
        this.chargerMembres(org);
        window.alert(res.message);
      },
      error: (err) => {
        this.bulkMobileSaving.set(false);
        const msg = err?.error?.message ?? 'Action groupée impossible.';
        window.alert(typeof msg === 'string' ? msg : 'Action groupée impossible.');
      },
    });
  }

  exportSelectedMembers(): void {
    const selected = this.selectedMembreIds();
    if (selected.size === 0) {
      alert('Veuillez sélectionner au moins un membre');
      return;
    }

    const selectedRows = this.filteredRows().filter((r) => selected.has(r.raw.id));

    const headers = [
      'Nom',
      'Code',
      'Poste',
      'Statut',
      'Ép. hebdo',
      'Ép. mois',
      'Solidarité',
      'Pénalité',
      'Amende',
      'Emprunt',
      'Adhésion',
    ];

    const data = selectedRows.map((r) => [
      r.raw.nomComplet,
      r.raw.codeMembre,
      r.poste.label,
      r.raw.actif ? 'Actif' : 'Suspendu',
      r.soldes.epargneHebdo.toString(),
      r.soldes.epargneMois.toString(),
      r.soldes.solidarite.toString(),
      r.soldes.penalite.toString(),
      r.soldes.amende.toString(),
      (r.empruntMontant ?? 0).toString(),
      r.adhesion,
    ]);

    const org = this.auth.currentOrgNom() ?? 'GIE';
    const date = new Date().toISOString().split('T')[0];
    const filename = `membres-selection-${org}-${date}.csv`;

    downloadCsv(filename, headers, data);
  }

  openModal(): void {
    this.modalEditId.set(null);
    this.modalError.set(null);
    this.modalPoste.set('simple');
    this.modalForm.reset({
      prenom: '',
      nom: '',
      email: '',
      telephone: '',
      dateAdhesion: this.todayIso(),
      pieceIdentite: '',
      epargneHebdo: true,
      epargneMois: true,
      solidarite: true,
      penalite: false,
      amende: false,
      nouveauModeleCode: '',
      nouveauModeleLibelle: '',
      envoyerEmailActivation: true,
      actif: true,
      paiementMobileActif: false,
    });
    this.modelesCompteSelection.set({});
    const oid = organisationCouranteId(this.route, this.auth);
    if (oid) {
      this.compteModeleService.lister(oid).subscribe({
        next: (list) => this.modelesCompte.set(list),
        error: () => this.modelesCompte.set([]),
      });
    }
    this.modalOpen.set(true);
  }

  openEditModal(membreId: number, event?: Event): void {
    event?.stopPropagation();
    const org = organisationCouranteId(this.route, this.auth);
    if (org == null) return;

    this.modalEditId.set(membreId);
    this.modalError.set(null);
    this.modalLoading.set(true);
    this.modalOpen.set(true);

    this.membreService.get(org, membreId).subscribe({
      next: (m) => {
        const poste = postePourMembre(m.codeMembre, m.poste);
        this.modalPoste.set(poste.kind);
        this.modalForm.reset({
          prenom: m.prenom,
          nom: m.nom,
          email: m.email ?? '',
          telephone: m.telephone ?? '',
          dateAdhesion: m.dateAdhesion?.slice(0, 10) ?? this.todayIso(),
          pieceIdentite: m.pieceIdentite ?? '',
          epargneHebdo: true,
          epargneMois: true,
          solidarite: true,
          penalite: false,
          amende: false,
          nouveauModeleCode: '',
          nouveauModeleLibelle: '',
          envoyerEmailActivation: false,
          actif: m.actif,
          paiementMobileActif: m.paiementMobileActif ?? false,
        });
        this.modalLoading.set(false);
      },
      error: (err) => {
        this.modalLoading.set(false);
        this.modalOpen.set(false);
        this.modalEditId.set(null);
        const msg = err?.error?.message ?? 'Impossible de charger le membre.';
        window.alert(typeof msg === 'string' ? msg : 'Impossible de charger le membre.');
      },
    });
  }

  closeModal(): void {
    this.modalOpen.set(false);
    this.modalEditId.set(null);
    this.modalSaving.set(false);
    this.modalLoading.set(false);
  }

  enregistrerMembre(): void {
    if (this.modalEnEdition()) {
      this.modifierMembre();
    } else {
      this.creerMembre();
    }
  }

  private modifierMembre(): void {
    if (this.modalForm.invalid) {
      this.modalForm.markAllAsTouched();
      return;
    }
    const org = organisationCouranteId(this.route, this.auth);
    const membreId = this.modalEditId();
    if (org == null || membreId == null) return;

    const v = this.modalForm.getRawValue();
    this.modalSaving.set(true);
    this.modalError.set(null);
    this.membreService
      .modifier(org, membreId, {
        prenom: v.prenom.trim(),
        nom: v.nom.trim(),
        email: v.email.trim() || undefined,
        telephone: v.telephone.trim() || undefined,
        dateAdhesion: v.dateAdhesion || undefined,
        pieceIdentite: v.pieceIdentite.trim() || undefined,
        poste: posteKindVersApi(this.modalPoste()),
        actif: v.actif,
        ...(this.peutConfigurerPaiementMobile()
          ? { paiementMobileActif: v.paiementMobileActif }
          : {}),
      })
      .subscribe({
        next: () => {
          this.modalSaving.set(false);
          this.closeModal();
          this.chargerMembres(org);
        },
        error: (err) => {
          this.modalSaving.set(false);
          const msg = err?.error?.message ?? 'Impossible de modifier le membre.';
          this.modalError.set(typeof msg === 'string' ? msg : 'Impossible de modifier le membre.');
        },
      });
  }

  creerMembre(): void {
    if (this.modalForm.invalid) {
      this.modalForm.markAllAsTouched();
      return;
    }
    const org = organisationCouranteId(this.route, this.auth);
    if (org == null) return;

    const v = this.modalForm.getRawValue();
    const modelesIds = Object.entries(this.modelesCompteSelection())
      .filter(([, checked]) => checked)
      .map(([id]) => Number(id));

    this.modalSaving.set(true);
    this.modalError.set(null);
    this.membreService
      .creer(org, {
        prenom: v.prenom.trim(),
        nom: v.nom.trim(),
        email: v.email.trim(),
        telephone: v.telephone.trim(),
        creerCompteAcces: true,
        envoyerEmailActivation: v.envoyerEmailActivation,
        dateAdhesion: v.dateAdhesion || undefined,
        pieceIdentite: v.pieceIdentite.trim() || undefined,
        poste: posteKindVersApi(this.modalPoste()),
        comptes: {
          epargneHebdo: v.epargneHebdo,
          epargneMois: v.epargneMois,
          solidarite: v.solidarite,
          penalite: v.penalite,
          amende: v.amende,
        },
        modelesCompteIds: modelesIds.length ? modelesIds : undefined,
        ...(this.peutConfigurerPaiementMobile()
          ? { paiementMobileActif: v.paiementMobileActif }
          : {}),
      })
      .subscribe({
        next: (m) => {
          this.membres.update((list) => [...list, m]);
          this.modalSaving.set(false);
          this.closeModal();
          this.chargerMembres(org);
        },
        error: (err) => {
          this.modalSaving.set(false);
          const msg = err?.error?.message ?? 'Impossible de créer le membre.';
          this.modalError.set(typeof msg === 'string' ? msg : 'Impossible de créer le membre.');
        },
      });
  }

  toggleModeleCompte(id: number): void {
    this.modelesCompteSelection.update((prev) => ({
      ...prev,
      [id]: !prev[id],
    }));
  }

  isModeleSelected(id: number): boolean {
    return !!this.modelesCompteSelection()[id];
  }

  ajouterModeleCompte(): void {
    const org = organisationCouranteId(this.route, this.auth);
    if (org == null) return;
    const code = this.modalForm.controls.nouveauModeleCode.value.trim().toUpperCase();
    const libelle = this.modalForm.controls.nouveauModeleLibelle.value.trim();
    if (!code || !libelle) {
      this.modalError.set('Code et libellé requis pour le nouveau type de compte.');
      return;
    }
    this.compteModeleService.creer(org, { code, libelle }).subscribe({
      next: (m) => {
        this.modelesCompte.update((list) => [...list, m]);
        this.modelesCompteSelection.update((prev) => ({ ...prev, [m.id]: true }));
        this.modalForm.patchValue({ nouveauModeleCode: '', nouveauModeleLibelle: '' });
        this.modalError.set(null);
      },
      error: (err) => {
        const msg = err?.error?.message ?? 'Impossible d’ajouter ce type de compte.';
        this.modalError.set(typeof msg === 'string' ? msg : 'Impossible d’ajouter ce type de compte.');
      },
    });
  }

  private chargerMembres(oid: number): void {
    this.chargerListe(oid);
  }

  private todayIso(): string {
    const d = new Date();
    return d.toISOString().slice(0, 10);
  }

  demanderSuppression(membreId: number, event: Event): void {
    event.stopPropagation();
    this.deleteConfirmId.set(membreId);
  }

  annulerSuppression(): void {
    this.deleteConfirmId.set(null);
  }

  confirmerSuppression(): void {
    const org = organisationCouranteId(this.route, this.auth);
    const id = this.deleteConfirmId();
    if (org == null || id == null) return;
    this.deleteSaving.set(true);
    this.membreService.supprimer(org, id).subscribe({
      next: () => {
        this.deleteSaving.set(false);
        this.deleteConfirmId.set(null);
        this.membres.update((list) => list.filter((m) => m.id !== id));
        this.chargerMembres(org);
      },
      error: (err) => {
        this.deleteSaving.set(false);
        const msg = err?.error?.message ?? 'Suppression impossible.';
        window.alert(typeof msg === 'string' ? msg : 'Suppression impossible.');
      },
    });
  }

  overlayClick(ev: MouseEvent): void {
    if ((ev.target as HTMLElement).classList.contains('modal-overlay')) {
      this.closeModal();
    }
  }

  openImportModal(): void {
    this.importModalOpen.set(true);
    this.importFichier.set(null);
    this.importResult.set(null);
    this.importError.set(null);
  }

  closeImportModal(): void {
    this.importModalOpen.set(false);
    this.importUploading.set(false);
    this.importDownloading.set(false);
  }

  importOverlayClick(ev: MouseEvent): void {
    if ((ev.target as HTMLElement).classList.contains('modal-overlay')) {
      this.closeImportModal();
    }
  }

  telechargerModeleImport(): void {
    const org = organisationCouranteId(this.route, this.auth);
    if (org == null) {
      this.telechargerModeleLocal();
      this.importError.set(
        'Organisation introuvable dans l’URL : le modèle CSV local a été téléchargé. Reconnectez-vous via le menu GIE si besoin.'
      );
      return;
    }
    this.importDownloading.set(true);
    this.importError.set(null);
    this.membreService.telechargerModeleImport(org).subscribe({
      next: (blob) => {
        if (!blob || blob.size < 100) {
          this.telechargerModeleLocal();
          this.importDownloading.set(false);
          this.importError.set('Réponse serveur invalide — modèle CSV local proposé à la place.');
          return;
        }
        this.enregistrerBlob(blob, 'modele-import-membres.xlsx');
        this.importDownloading.set(false);
      },
      error: () => {
        this.telechargerModeleLocal();
        this.importDownloading.set(false);
        this.importError.set(
          'Modèle Excel indisponible sur le serveur — le modèle CSV local a été téléchargé.'
        );
      },
    });
  }

  /** Téléchargement direct du fichier dans assets (sans API). */
  telechargerModeleLocal(): void {
    const a = document.createElement('a');
    a.href = this.modeleImportUrl;
    a.download = 'modele-import-membres.csv';
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    a.remove();
  }

  private enregistrerBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  onFichierImportSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.importFichier.set(file);
    this.importResult.set(null);
    this.importError.set(null);
    input.value = '';
  }

  lancerImportMembres(): void {
    const org = organisationCouranteId(this.route, this.auth);
    const file = this.importFichier();
    if (org == null || !file) {
      this.importError.set('Sélectionnez un fichier Excel (.xlsx).');
      return;
    }
    this.importUploading.set(true);
    this.importError.set(null);
    this.importResult.set(null);
    this.membreService.importerFichier(org, file).subscribe({
      next: (res) => {
        this.importUploading.set(false);
        this.importResult.set(res);
        if (res.membresCrees > 0) {
          this.chargerMembres(org);
        }
      },
      error: (err) => {
        this.importUploading.set(false);
        const msg = err?.error?.message ?? 'Import impossible.';
        this.importError.set(typeof msg === 'string' ? msg : 'Import impossible.');
      },
    });
  }

  readonly formatFcfa = formatFcfa;

  /** Affiche le solde ou « — » si le compte n’existe pas (0 sans mouvement affiché comme 0 F). */
  formatSoldeCompte(montant: number): string {
    return this.formatFcfa(montant);
  }

  private initialsFrom(name: string): string {
    return name
      .split(' ')
      .filter(Boolean)
      .map((p) => p[0])
      .join('')
      .slice(0, 2)
      .toUpperCase();
  }

  private formatAdhesion(iso?: string): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '—';
    return new Intl.DateTimeFormat('fr-FR', { month: 'short', year: 'numeric' }).format(d);
  }

  pickModalPoste(p: PosteKind): void {
    this.modalPoste.set(p);
  }

  /** Id organisation affichée (URL shell), pour liens et navigation. */
  orgCourante(): number | null {
    return organisationCouranteId(this.route, this.auth);
  }

  /** Navigation fiche membre (ligne ou bouton œil). */
  voirFiche(membreId: number | string, ev?: Event): void {
    ev?.preventDefault();
    ev?.stopPropagation();
    const org = organisationCouranteId(this.route, this.auth);
    const mid = Number(membreId);
    if (org == null || Number.isNaN(mid)) return;
    void this.router.navigate(['/organisations', org, 'membres', mid]);
  }

  /** Export filtered members as CSV */
  exportMembres(): void {
    const rows = this.filteredRows();
    if (rows.length === 0) {
      alert('Aucun membre à exporter');
      return;
    }

    const headers = [
      'Nom',
      'Code',
      'Poste',
      'Statut',
      'Ép. hebdo',
      'Ép. mois',
      'Solidarité',
      'Pénalité',
      'Amende',
      'Emprunt',
      'Adhésion',
    ];

    const data = rows.map((r) => [
      r.raw.nomComplet,
      r.raw.codeMembre,
      r.poste.label,
      r.raw.actif ? 'Actif' : 'Suspendu',
      r.soldes.epargneHebdo.toString(),
      r.soldes.epargneMois.toString(),
      r.soldes.solidarite.toString(),
      r.soldes.penalite.toString(),
      r.soldes.amende.toString(),
      (r.empruntMontant ?? 0).toString(),
      r.adhesion,
    ]);

    const org = this.auth.currentOrgNom() ?? 'GIE';
    const date = new Date().toISOString().split('T')[0];
    const filename = `membres-${org}-${date}.csv`;

    downloadCsv(filename, headers, data);
  }

  /** Keyboard shortcuts */
  @HostListener('document:keydown', ['$event'])
  handleKeyboardEvent(event: KeyboardEvent): void {
    // Ctrl+N: New member
    if (event.ctrlKey && event.key === 'n') {
      event.preventDefault();
      this.openModal();
    }
    // Ctrl+E: Export
    if (event.ctrlKey && event.key === 'e') {
      event.preventDefault();
      this.exportMembres();
    }
    // Ctrl+F: Focus search (prevent browser search)
    if (event.ctrlKey && event.key === 'f') {
      event.preventDefault();
      const searchInput = document.querySelector('.filter-search') as HTMLInputElement;
      if (searchInput) {
        searchInput.focus();
        searchInput.select();
      }
    }
    // Escape: Close modal
    if (event.key === 'Escape') {
      if (this.modalOpen()) {
        this.closeModal();
      }
      if (this.importModalOpen()) {
        this.closeImportModal();
      }
    }
  }
}
