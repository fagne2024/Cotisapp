import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';
import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import { paginateSlice } from '../../shared/util/pagination.util';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { MembreDto, MembreService } from '../../core/services/membre.service';
import { NotificationService } from '../../core/services/notification.service';
import {
  CreateUtilisateurOrgBody,
  UtilisateurAccesService,
  UtilisateurOrgDto,
} from '../../core/services/utilisateur-acces.service';
import {
  CreateTypeProfilBody,
  NiveauDroitApi,
  TypeProfilDroitDto,
  TypeProfilDto,
  TypeProfilService,
  UpdateTypeProfilBody,
} from '../../core/services/type-profil.service';
import {
  JournalUtilisateurService,
  TYPES_EVENEMENT_JOURNAL,
  TypeEvenementJournal,
  JournalUtilisateurDto,
} from '../../core/services/journal-utilisateur.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import { DROIT_ACTION_IMPORTS } from '../../shared/imports/droit-action.imports';
import { postePourMembre, PosteMembreApi } from '../membres/membres-poste.util';
import {
  avatarColor,
  cellClass,
  cellLabel,
  initials,
  MATRICE_DROITS,
  NIVEAUX_DROIT_OPTIONS,
  logDroits,
  niveauDroitLabel,
  NiveauDroitUi,
  resumeNiveauxDroits,
  UtilisateursTab,
  RoleFormUi,
  roleApiDepuisFormUi,
  estPosteBureau,
  PROFILS_BUREAU_RAPIDES,
} from './utilisateurs-droits.util';
import {
  appliquerModuleSection,
  calculerModulesDepuisLignes,
  estSectionOrgConfigurable,
  libellesModulesMenuActifs,
  moduleSectionActif,
} from './droits-modules.util';
import { DroitAccesService } from '../../core/services/droit-acces.service';

const POSTES_FORM: { api: PosteMembreApi; label: string }[] = [
  { api: 'SIMPLE', label: '👤 Membre simple' },
  { api: 'PRESIDENT', label: '👑 Président(e)' },
  { api: 'SECRETAIRE_GENERAL', label: '📝 Secrétaire Général(e)' },
  { api: 'SECRETAIRE_GENERAL_ADJOINT', label: '📋 S.G. Adjoint(e)' },
  { api: 'TRESORIER', label: '💼 Trésorier(ère)' },
  { api: 'TRESORIER_ADJOINT', label: '💼 Trésorier(ère) adjoint' },
  { api: 'COMMISSAIRE_AUX_COMPTES', label: '📊 Commissaire au compte' },
  { api: 'SUPERVISEUR', label: '🔍 Superviseur' },
];

@Component({
  selector: 'app-utilisateurs-droits',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, RouterLink, NgTemplateOutlet, ListPaginationComponent, DatePipe, ...DROIT_ACTION_IMPORTS],
  templateUrl: './utilisateurs-droits.component.html',
  styleUrls: [
    './utilisateurs-droits.component.scss',
    '../../shared/styles/pagination.scss',
  ],
})
export class UtilisateursDroitsComponent implements OnInit, OnDestroy {
  readonly Math = Math;
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly accesService = inject(UtilisateurAccesService);
  private readonly membreService = inject(MembreService);
  private readonly notify = inject(NotificationService);
  private readonly typeProfilApi = inject(TypeProfilService);
  private readonly journalApi = inject(JournalUtilisateurService);
  private readonly droitsAcces = inject(DroitAccesService);

  readonly matrice = MATRICE_DROITS;
  readonly typesEvenementJournal = TYPES_EVENEMENT_JOURNAL;
  readonly postesForm = POSTES_FORM;
  readonly profilsBureauRapides = PROFILS_BUREAU_RAPIDES;
  readonly cellLabel = cellLabel;
  readonly cellClass = cellClass;
  readonly initials = initials;
  readonly avatarColor = avatarColor;

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly users = signal<UtilisateurOrgDto[]>([]);
  readonly stats = signal({ total: 0, actifs: 0, suspendus: 0, connectesMaintenant: 0 });
  readonly membresSansCompte = signal<MembreDto[]>([]);
  readonly modalOpen = signal(false);
  readonly editingUser = signal<UtilisateurOrgDto | null>(null);
  readonly activeTab = signal<UtilisateursTab>('users');
  readonly searchQuery = signal('');
  readonly pageUsers = signal(1);
  readonly pageSizeUsers = 10;
  readonly filtreRole = signal<'ALL' | 'ADMIN_GIE' | 'MEMBRE'>('ALL');
  readonly filtreStatut = signal<'ALL' | 'ACTIF' | 'SUSPENDU'>('ALL');
  readonly filtrePoste = signal<'ALL' | PosteMembreApi>('ALL');
  readonly roleFormUi = signal<RoleFormUi>('MEMBRE_BUREAU');
  readonly typesProfil = signal<TypeProfilDto[]>([]);
  readonly savingTypeProfil = signal(false);
  readonly typeFormRole = signal<'ADMIN_GIE' | 'MEMBRE'>('MEMBRE');
  readonly typeForm = this.fb.nonNullable.group({
    code: ['', Validators.required],
    libelle: ['', Validators.required],
    role: ['MEMBRE' as 'ADMIN_GIE' | 'MEMBRE'],
    posteMembre: ['SIMPLE' as PosteMembreApi],
    canalConnexion: ['TELEPHONE' as 'EMAIL' | 'TELEPHONE' | 'LES_DEUX'],
  });

  readonly typeRoleMembre = computed(() => this.typeFormRole() === 'MEMBRE');
  readonly niveauxDroitOptions = NIVEAUX_DROIT_OPTIONS;
  readonly niveauDroitLabel = niveauDroitLabel;

  readonly typeEditModal = signal<TypeProfilDto | null>(null);
  readonly droitsModal = signal<TypeProfilDto | null>(null);
  readonly droitsLignes = signal<TypeProfilDroitDto[]>([]);
  /** Niveaux tels que chargés depuis l'API (pour comparer avant enregistrement). */
  private droitsSnapshotApi: Record<string, NiveauDroitUi> = {};
  private typesProfilChargementEnCours = false;
  readonly droitsChargement = signal(false);
  readonly droitsActionsCount = computed(() => this.droitsLignes().length);
  readonly droitsPretPourEnregistrement = computed(
    () => !this.droitsChargement() && this.droitsLignes().length > 0
  );

  /** Aperçu des entrées du menu GIE pour le profil en cours d'édition. */
  readonly modulesMenuPreview = computed(() => {
    const lignes = this.droitsLignes();
    if (!lignes.length) {
      return { actifs: [] as string[], modules: {} as Record<string, boolean> };
    }
    const modules = calculerModulesDepuisLignes(lignes);
    return { actifs: libellesModulesMenuActifs(modules), modules };
  });
  readonly savingDroits = signal(false);
  readonly typesProfilSelect = signal<TypeProfilDto[]>([]);

  readonly profilsApplicatifsGroupes = computed(() => {
    const types = this.typesProfilSelect();
    return {
      admin: types.filter((t) => t.role === 'ADMIN_GIE'),
      bureau: types
        .filter((t) => t.role === 'MEMBRE' && estPosteBureau(t.posteMembre))
        .sort((a, b) => a.ordre - b.ordre || a.libelle.localeCompare(b.libelle)),
      membreSimple: types.filter(
        (t) => t.role === 'MEMBRE' && (!t.posteMembre || t.posteMembre === 'SIMPLE')
      ),
    };
  });

  readonly typeEditForm = this.fb.nonNullable.group({
    libelle: ['', Validators.required],
    posteMembre: ['SIMPLE' as PosteMembreApi],
    canalConnexion: ['TELEPHONE' as 'EMAIL' | 'TELEPHONE' | 'LES_DEUX'],
    actif: [true],
  });

  readonly typeEditRole = signal<'ADMIN_GIE' | 'MEMBRE'>('MEMBRE');
  readonly typeEditRoleMembre = computed(() => this.typeEditRole() === 'MEMBRE');

  private connectesRefreshTimer: ReturnType<typeof setInterval> | null = null;

  readonly form = this.fb.nonNullable.group({
    prenom: ['', Validators.required],
    nom: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    membreId: [null as number | null],
    typeProfilId: [null as number | null],
    poste: ['SIMPLE' as PosteMembreApi],
    compteActif: [true],
    envoyerActivation: [true],
    forcerChgtMdp: [true],
    mobileAutorise: [true],
  });

  readonly filteredUsers = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    return this.users().filter((u) => {
      if (this.filtreRole() !== 'ALL' && u.role !== this.filtreRole()) return false;
      if (this.filtreStatut() === 'ACTIF' && !u.actif) return false;
      if (this.filtreStatut() === 'SUSPENDU' && u.actif) return false;
      if (this.filtrePoste() !== 'ALL') {
        const p = u.poste ?? 'SIMPLE';
        if (p !== this.filtrePoste()) return false;
      }
      if (!q) return true;
      return (
        u.nomComplet.toLowerCase().includes(q) ||
        (u.telephone?.toLowerCase().includes(q) ?? false) ||
        u.email.toLowerCase().includes(q) ||
        (u.codeMembre?.toLowerCase().includes(q) ?? false)
      );
    });
  });

  readonly paginatedUsers = computed(() =>
    paginateSlice(this.filteredUsers(), this.pageUsers(), this.pageSizeUsers)
  );

  readonly connectes = computed(() => this.users().filter((u) => u.enLigne && u.actif));

  readonly adminGieExiste = computed(() => this.users().some((u) => u.role === 'ADMIN_GIE' && u.actif));

  readonly journalEntries = signal<JournalUtilisateurDto[]>([]);
  readonly journalTotal = signal(0);
  readonly journalLoading = signal(false);
  readonly journalPage = signal(1);
  readonly journalPageSize = 25;
  readonly journalTypeFiltre = signal<TypeEvenementJournal | ''>('');
  readonly journalSuccesFiltre = signal<'ALL' | 'OK' | 'KO'>('ALL');
  readonly journalSearch = signal('');
  readonly journalUtilisateurFiltre = signal<number | null>(null);

  readonly journalCount = computed(() => this.journalTotal());
  readonly paginatedJournal = computed(() => this.journalEntries());

  orgId(): number | null {
    return organisationCouranteId(this.route, this.auth);
  }

  telephoneAffiche(u: UtilisateurOrgDto): string {
    const t = u.telephone?.trim();
    return t ? t : '—';
  }

  onPageUsersChange(p: number): void {
    this.pageUsers.set(p);
  }

  profilLink(): (string | number)[] | null {
    const orgId = organisationCouranteId(this.route, this.auth);
    if (!orgId) {
      return null;
    }
    return ['/organisations', orgId, 'mon-profil'];
  }

  private reinitialiserPageUtilisateurs(): void {
    this.pageUsers.set(1);
  }

  onFiltreRoleChange(event: Event): void {
    this.filtreRole.set((event.target as HTMLSelectElement).value as 'ALL' | 'ADMIN_GIE' | 'MEMBRE');
    this.reinitialiserPageUtilisateurs();
  }

  onFiltreStatutChange(event: Event): void {
    this.filtreStatut.set((event.target as HTMLSelectElement).value as 'ALL' | 'ACTIF' | 'SUSPENDU');
    this.reinitialiserPageUtilisateurs();
  }

  onFiltrePosteChange(event: Event): void {
    this.filtrePoste.set((event.target as HTMLSelectElement).value as 'ALL' | PosteMembreApi);
    this.reinitialiserPageUtilisateurs();
  }

  ngOnInit(): void {
    this.appliquerReglesTypeForm();
    this.route.queryParamMap.subscribe((params) => {
      const tab = params.get('tab');
      if (tab === 'journal' || tab === 'droits' || tab === 'types') {
        this.activeTab.set(tab);
        if (tab === 'types' || tab === 'droits') {
          this.chargerTypesProfil();
        }
        if (tab === 'journal') {
          this.chargerJournal();
        }
      } else {
        this.activeTab.set('users');
      }
    });
    this.charger();
    this.chargerJournalCount();
    this.connectesRefreshTimer = setInterval(() => {
      if (this.activeTab() === 'users') {
        this.rafraichirConnectes();
      }
    }, 30_000);
  }

  ngOnDestroy(): void {
    if (this.connectesRefreshTimer != null) {
      clearInterval(this.connectesRefreshTimer);
    }
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey) {
      if (event.key === '1') {
        event.preventDefault();
        this.setTab('users');
      } else if (event.key === '2') {
        event.preventDefault();
        this.setTab('journal');
      } else if (event.key === '3') {
        event.preventDefault();
        this.setTab('types');
      } else if (event.key === '4') {
        event.preventDefault();
        this.setTab('droits');
      } else if (event.key === 'n' || event.key === 'N') {
        event.preventDefault();
        this.openModal();
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.closeModal();
      this.searchQuery.set('');
    }
  }

  rafraichirConnectes(): void {
    const orgId = this.orgId();
    if (orgId == null) return;
    this.accesService.stats(orgId).subscribe({
      next: (s) => this.stats.set(s),
    });
    this.accesService.lister(orgId).subscribe({
      next: (list) => this.users.set(list),
    });
  }

  charger(): void {
    const orgId = this.orgId();
    if (orgId == null) return;
    this.loading.set(true);
    this.accesService.stats(orgId).subscribe({
      next: (s) => this.stats.set(s),
      error: () => this.notify.error('Impossible de charger les statistiques'),
    });
    this.typeProfilApi.lister(orgId).subscribe({
      next: (types) => this.typesProfilSelect.set(types),
    });
    this.chargerTypesProfil();
    this.accesService.lister(orgId).subscribe({
      next: (list) => {
        this.users.set(list);
        this.loading.set(false);
        this.chargerMembresSansCompte(orgId, list);
      },
      error: () => {
        this.loading.set(false);
        this.notify.error('Impossible de charger les utilisateurs');
      },
    });
  }

  private chargerMembresSansCompte(orgId: number, users: UtilisateurOrgDto[]): void {
    this.membreService.lister(orgId, true).subscribe({
      next: (membres) => {
        this.membresSansCompte.set(
          membres.filter((m) => m.actif && !m.compteAcces && m.utilisateurId == null)
        );
      },
    });
  }

  chargerTypesProfil(): void {
    const orgId = this.orgId();
    if (!orgId) {
      return;
    }
    if (this.typesProfilChargementEnCours) {
      logDroits('chargerTypesProfil : ignoré (requête déjà en cours)');
      return;
    }
    this.typesProfilChargementEnCours = true;
    logDroits('chargerTypesProfil → début', { orgId });
    this.typeProfilApi.listerGestion(orgId).subscribe({
      next: (t) => {
        this.typesProfilChargementEnCours = false;
        logDroits('chargerTypesProfil ← OK', {
          count: t.length,
          profils: t.map((p) => ({ id: p.id, code: p.code, libelle: p.libelle })),
        });
        this.typesProfil.set(t);
        if (this.activeTab() === 'droits') {
          this.preselectionnerProfilDroits();
        }
      },
      error: (err) => {
        this.typesProfilChargementEnCours = false;
        logDroits('chargerTypesProfil ← ERREUR', err);
        this.notify.show('Impossible de charger les types de profil.');
      },
    });
  }

  onTypeRoleChange(): void {
    this.typeFormRole.set(this.typeForm.controls.role.value);
    this.appliquerReglesTypeForm();
  }

  onTypeFormSubmit(): void {
    this.ajouterTypeProfil();
  }

  private appliquerReglesTypeForm(): void {
    const role = this.typeFormRole();
    if (role === 'ADMIN_GIE') {
      this.typeForm.patchValue({ canalConnexion: 'EMAIL' }, { emitEvent: false });
      this.typeForm.controls.canalConnexion.disable({ emitEvent: false });
    } else {
      this.typeForm.controls.canalConnexion.enable({ emitEvent: false });
      if (this.typeForm.controls.canalConnexion.value === 'EMAIL') {
        this.typeForm.patchValue({ canalConnexion: 'TELEPHONE' }, { emitEvent: false });
      }
    }
  }

  ajouterTypeProfil(): void {
    const orgId = this.orgId();
    if (!orgId) {
      return;
    }
    const code = this.typeForm.controls.code.value.trim();
    const libelle = this.typeForm.controls.libelle.value.trim();
    this.typeForm.patchValue({ code, libelle }, { emitEvent: false });
    if (!code || !libelle) {
      this.typeForm.markAllAsTouched();
      this.notify.error('Renseignez le code et le libellé du type de profil.');
      return;
    }
    const raw = this.typeForm.getRawValue();
    const body: CreateTypeProfilBody = {
      code,
      libelle,
      role: raw.role,
      posteMembre: raw.role === 'MEMBRE' ? raw.posteMembre : null,
      canalConnexion: raw.role === 'ADMIN_GIE' ? 'EMAIL' : raw.canalConnexion,
    };
    this.savingTypeProfil.set(true);
    this.typeProfilApi.creer(orgId, body).subscribe({
      next: () => {
        this.savingTypeProfil.set(false);
        this.notify.success('Type de profil ajouté.');
        this.typeForm.reset({
          code: '',
          libelle: '',
          role: 'MEMBRE',
          posteMembre: 'SIMPLE',
          canalConnexion: 'TELEPHONE',
        });
        this.typeFormRole.set('MEMBRE');
        this.appliquerReglesTypeForm();
        this.chargerTypesProfil();
      },
      error: (err) => {
        this.savingTypeProfil.set(false);
        this.notify.error(err?.error?.message ?? 'Échec de la création du type de profil.');
      },
    });
  }

  chargerJournalCount(): void {
    const orgId = this.orgId();
    if (orgId == null) {
      return;
    }
    this.journalApi.compter(orgId).subscribe({
      next: (r) => this.journalTotal.set(r.total),
      error: () => undefined,
    });
  }

  chargerJournal(): void {
    const orgId = this.orgId();
    if (orgId == null) {
      return;
    }
    this.journalLoading.set(true);
    const succes =
      this.journalSuccesFiltre() === 'OK'
        ? true
        : this.journalSuccesFiltre() === 'KO'
          ? false
          : undefined;
    this.journalApi
      .lister(orgId, {
        utilisateurId: this.journalUtilisateurFiltre() ?? undefined,
        type: this.journalTypeFiltre() || undefined,
        succes,
        search: this.journalSearch(),
        page: this.journalPage() - 1,
        size: this.journalPageSize,
      })
      .subscribe({
        next: (page) => {
          this.journalEntries.set(page.content);
          this.journalTotal.set(page.totalElements);
          this.journalLoading.set(false);
        },
        error: (err) => {
          this.journalLoading.set(false);
          this.notify.error(err?.error?.message ?? 'Impossible de charger le journal.');
        },
      });
  }

  onJournalTypeChange(event: Event): void {
    this.journalTypeFiltre.set((event.target as HTMLSelectElement).value as TypeEvenementJournal | '');
    this.journalPage.set(1);
    this.chargerJournal();
  }

  onJournalSuccesChange(event: Event): void {
    this.journalSuccesFiltre.set((event.target as HTMLSelectElement).value as 'ALL' | 'OK' | 'KO');
    this.journalPage.set(1);
    this.chargerJournal();
  }

  onJournalUtilisateurChange(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    this.journalUtilisateurFiltre.set(raw ? Number(raw) : null);
    this.journalPage.set(1);
    this.chargerJournal();
  }

  onJournalSearchInput(event: Event): void {
    this.journalSearch.set((event.target as HTMLInputElement).value);
  }

  appliquerJournalSearch(): void {
    this.journalPage.set(1);
    this.chargerJournal();
  }

  onJournalPageChange(p: number): void {
    this.journalPage.set(p);
    this.chargerJournal();
  }

  journalBadgeClass(e: JournalUtilisateurDto): string {
    if (!e.succes) {
      return 'jr-ko';
    }
    if (e.typeEvenement === 'CONNEXION' || e.typeEvenement === 'DECONNEXION') {
      return 'jr-ok';
    }
    if (e.typeEvenement === 'MODULE_VISITE' || e.typeEvenement === 'NAVIGATION') {
      return 'jr-info';
    }
    return 'jr-ok';
  }

  journalIcon(e: JournalUtilisateurDto): string {
    switch (e.typeEvenement) {
      case 'CONNEXION':
        return '✅';
      case 'DECONNEXION':
        return '🚪';
      case 'CONNEXION_ECHEC':
        return '⛔';
      case 'MODULE_VISITE':
      case 'NAVIGATION':
        return '📂';
      case 'SECURITE':
        return '🔒';
      default:
        return '⚙️';
    }
  }

  setTab(tab: UtilisateursTab): void {
    this.activeTab.set(tab);
    const orgId = this.orgId();
    if (orgId == null) {
      return;
    }
    if (tab === 'types' || tab === 'droits') {
      this.chargerTypesProfil();
    }
    if (tab === 'journal') {
      this.chargerJournal();
    }
    const qp = tab === 'users' ? {} : { tab };
    void this.router.navigate(['/organisations', orgId, 'gestion', 'utilisateurs'], {
      queryParams: qp,
    });
  }

  posteMeta(u: UtilisateurOrgDto) {
    return postePourMembre(u.codeMembre ?? '', u.poste);
  }

  roleLabel(role: string): string {
    return role === 'ADMIN_GIE' ? 'Admin GIE' : 'Membre';
  }

  roleAffichage(u: UtilisateurOrgDto): string {
    if (u.role === 'ADMIN_GIE') {
      return u.typeProfilLibelle ?? 'Admin GIE';
    }
    if (u.typeProfilLibelle) {
      return u.typeProfilLibelle;
    }
    return this.posteMeta(u).label;
  }

  selProfilBureauRapide(code: string, poste: PosteMembreApi): void {
    this.roleFormUi.set('MEMBRE_BUREAU');
    const match =
      this.profilsApplicatifsGroupes().bureau.find((t) => t.code === code) ??
      this.profilsApplicatifsGroupes().bureau.find((t) => t.posteMembre === poste);
    this.form.patchValue({
      poste,
      typeProfilId: match?.id ?? null,
    });
  }

  reinitialiserDroitsProfilsSysteme(): void {
    const orgId = this.orgId();
    if (orgId == null) {
      return;
    }
    if (
      !confirm(
        'Réappliquer les droits par défaut pour SG, SGA, Trésorier, Superviseur, Président et Membre ?\nLes personnalisations sur ces profils seront remplacées.'
      )
    ) {
      return;
    }
    this.typeProfilApi.reinitialiserDroitsSysteme(orgId).subscribe({
      next: () => {
        this.notify.success('Droits des profils système réalignés.');
        this.chargerTypesProfil();
        if (this.droitsModal()) {
          const t = this.droitsModal();
          if (t) {
            this.chargerDroitsPourProfil(t);
          }
        }
      },
      error: (err) =>
        this.notify.error(err?.error?.message ?? 'Réinitialisation des droits impossible.'),
    });
  }

  posteTypeProfilLabel(poste: PosteMembreApi | null | undefined): string {
    if (!poste) return '—';
    return this.postesForm.find((p) => p.api === poste)?.label ?? poste;
  }

  canalLabel(canal: string): string {
    switch (canal) {
      case 'EMAIL':
        return 'Email';
      case 'LES_DEUX':
        return 'Email ou téléphone';
      default:
        return 'Téléphone';
    }
  }

  lastLoginClass(libelle: string): string {
    if (!libelle || libelle === 'Jamais') return 'never';
    if (libelle.toLowerCase().includes('aujourd')) return 'recent';
    if (libelle.toLowerCase().includes('hier')) return 'recent';
    const m = libelle.match(/^(\d+)\s+jour/);
    if (m && Number(m[1]) <= 7) return 'recent';
    if (libelle.includes('mois') || libelle.includes('an')) return 'old';
    return '';
  }

  openModal(user?: UtilisateurOrgDto): void {
    this.editingUser.set(user ?? null);
    if (user) {
      const ui: RoleFormUi =
        user.role === 'ADMIN_GIE'
          ? 'ADMIN_GIE'
          : estPosteBureau(user.poste)
            ? 'MEMBRE_BUREAU'
            : 'MEMBRE_SIMPLE';
      this.roleFormUi.set(ui);
      this.form.reset({
        prenom: user.prenom,
        nom: user.nom,
        email: user.email,
        membreId: user.membreId,
        poste: user.poste ?? 'SIMPLE',
        compteActif: user.actif,
        envoyerActivation: false,
        forcerChgtMdp: false,
        mobileAutorise: true,
        typeProfilId: null,
      });
    } else {
      this.roleFormUi.set('MEMBRE_BUREAU');
      this.form.reset({
        prenom: '',
        nom: '',
        email: '',
        membreId: null,
        poste: 'SECRETAIRE_GENERAL',
        compteActif: true,
        envoyerActivation: true,
        forcerChgtMdp: true,
        mobileAutorise: true,
        typeProfilId: null,
      });
      this.proposerTypeProfilPourPoste();
    }
    this.modalOpen.set(true);
  }

  closeModal(): void {
    this.modalOpen.set(false);
    this.editingUser.set(null);
  }

  selRoleUi(ui: RoleFormUi): void {
    this.roleFormUi.set(ui);
    if (ui === 'ADMIN_GIE') {
      this.form.patchValue({ membreId: null, poste: 'SIMPLE' });
      const adminType =
        this.profilsApplicatifsGroupes().admin.find((t) => t.code === 'ADMIN_GIE') ??
        this.profilsApplicatifsGroupes().admin[0];
      this.form.patchValue({ typeProfilId: adminType?.id ?? null });
      return;
    }
    if (ui === 'MEMBRE_SIMPLE') {
      this.form.patchValue({ poste: 'SIMPLE' });
      const simple =
        this.profilsApplicatifsGroupes().membreSimple.find((t) => t.code === 'MEMBRE') ??
        this.profilsApplicatifsGroupes().membreSimple[0];
      this.form.patchValue({ typeProfilId: simple?.id ?? null });
      return;
    }
    const poste = this.form.controls.poste.value;
    if (!estPosteBureau(poste)) {
      this.form.patchValue({ poste: 'SECRETAIRE_GENERAL' });
    }
    this.proposerTypeProfilPourPoste();
  }

  typesProfilFiltresPourUtilisateur(): TypeProfilDto[] {
    const g = this.profilsApplicatifsGroupes();
    const ui = this.roleFormUi();
    if (ui === 'ADMIN_GIE') {
      return g.admin;
    }
    if (ui === 'MEMBRE_BUREAU') {
      return g.bureau;
    }
    return g.membreSimple;
  }

  libelleProfilApplicatif(tp: TypeProfilDto): string {
    if (tp.role === 'ADMIN_GIE') {
      return `${tp.libelle} — tous les droits organisation`;
    }
    if (tp.posteMembre && estPosteBureau(tp.posteMembre)) {
      const meta = postePourMembre('', tp.posteMembre as PosteMembreApi);
      return `${tp.libelle} — ${meta.label}`;
    }
    return `${tp.libelle} — accès personnel`;
  }

  onTypeProfilUtilisateurChange(): void {
    const id = this.form.controls.typeProfilId.value;
    if (id == null) {
      return;
    }
    const tp = this.typesProfilSelect().find((t) => t.id === id);
    if (tp?.posteMembre && estPosteBureau(tp.posteMembre) && this.roleFormUi() === 'MEMBRE_BUREAU') {
      this.form.patchValue({ poste: tp.posteMembre });
    }
  }

  proposerTypeProfilPourPoste(): void {
    if (this.roleFormUi() === 'ADMIN_GIE') {
      return;
    }
    if (this.roleFormUi() === 'MEMBRE_SIMPLE') {
      const simple =
        this.profilsApplicatifsGroupes().membreSimple.find((t) => t.code === 'MEMBRE') ??
        this.profilsApplicatifsGroupes().membreSimple[0];
      this.form.patchValue({ typeProfilId: simple?.id ?? null });
      return;
    }
    const poste = this.form.controls.poste.value;
    const match = this.profilsApplicatifsGroupes().bureau.find((t) => t.posteMembre === poste);
    this.form.patchValue({ typeProfilId: match?.id ?? null });
  }

  openTypeEdit(t: TypeProfilDto): void {
    this.typeEditRole.set(t.role === 'ADMIN_GIE' ? 'ADMIN_GIE' : 'MEMBRE');
    this.typeEditForm.reset({
      libelle: t.libelle,
      posteMembre: t.posteMembre ?? 'SIMPLE',
      canalConnexion: t.canalConnexion,
      actif: t.actif,
    });
    this.appliquerReglesTypeEditForm();
    this.typeEditModal.set(t);
  }

  closeTypeEdit(): void {
    this.typeEditModal.set(null);
  }

  private appliquerReglesTypeEditForm(): void {
    const role = this.typeEditRole();
    if (role === 'ADMIN_GIE') {
      this.typeEditForm.patchValue({ canalConnexion: 'EMAIL' }, { emitEvent: false });
      this.typeEditForm.controls.canalConnexion.disable({ emitEvent: false });
    } else {
      this.typeEditForm.controls.canalConnexion.enable({ emitEvent: false });
    }
  }

  enregistrerTypeEdit(): void {
    const orgId = this.orgId();
    const t = this.typeEditModal();
    if (!orgId || !t || this.typeEditForm.invalid) {
      this.typeEditForm.markAllAsTouched();
      return;
    }
    const raw = this.typeEditForm.getRawValue();
    const body: UpdateTypeProfilBody = {
      libelle: raw.libelle.trim(),
      posteMembre: t.role === 'MEMBRE' ? raw.posteMembre : null,
      canalConnexion: t.role === 'ADMIN_GIE' ? 'EMAIL' : raw.canalConnexion,
      actif: raw.actif,
    };
    this.savingTypeProfil.set(true);
    this.typeProfilApi.modifier(orgId, t.id, body).subscribe({
      next: () => {
        this.savingTypeProfil.set(false);
        this.notify.success('Type de profil mis à jour.');
        this.closeTypeEdit();
        this.chargerTypesProfil();
        this.typeProfilApi.lister(orgId).subscribe({ next: (types) => this.typesProfilSelect.set(types) });
      },
      error: (err) => {
        this.savingTypeProfil.set(false);
        this.notify.error(err?.error?.message ?? 'Échec de la mise à jour.');
      },
    });
  }

  confirmerSupprimerType(t: TypeProfilDto): void {
    if (t.systeme) {
      this.notify.error('Les types par défaut ne peuvent pas être supprimés.');
      return;
    }
    if (!confirm(`Supprimer le type « ${t.libelle} » ?`)) return;
    const orgId = this.orgId();
    if (!orgId) return;
    this.typeProfilApi.supprimer(orgId, t.id).subscribe({
      next: () => {
        this.notify.success('Type de profil supprimé.');
        this.chargerTypesProfil();
        this.typeProfilApi.lister(orgId).subscribe({ next: (types) => this.typesProfilSelect.set(types) });
      },
      error: (err) => this.notify.error(err?.error?.message ?? 'Suppression impossible.'),
    });
  }

  openDroitsModal(t: TypeProfilDto): void {
    this.chargerDroitsPourProfil(t);
    setTimeout(() => {
      document.getElementById('droits-profil-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 80);
  }

  onDroitsProfilSelect(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    const id = Number(raw);
    logDroits('sélection profil (liste)', { raw, id });
    if (!Number.isFinite(id)) {
      this.closeDroitsModal();
      return;
    }
    const t = this.typesProfil().find((p) => p.id === id);
    if (t) {
      this.chargerDroitsPourProfil(t);
    } else {
      logDroits('sélection profil : type introuvable dans typesProfil()', { id });
    }
  }

  private preselectionnerProfilDroits(): void {
    const courant = this.droitsModal();
    const types = this.typesProfil();
    logDroits('preselectionnerProfilDroits', {
      courantId: courant?.id ?? null,
      lignesChargees: this.droitsLignes().length,
      chargement: this.droitsChargement(),
      typesCount: types.length,
    });
    if (courant) {
      const aJour = types.find((p) => p.id === courant.id);
      if (aJour) {
        this.droitsModal.set(aJour);
      }
      if (this.droitsChargement()) {
        logDroits('preselectionner → GET droits déjà en cours');
      } else if (this.droitsLignes().length === 0) {
        logDroits('preselectionner → chargement API (profil courant)');
        this.chargerDroitsPourProfil(aJour ?? courant);
      } else {
        logDroits('preselectionner → lignes déjà en mémoire', {
          lignes: this.droitsLignes().length,
        });
      }
      return;
    }
    if (types.length > 0) {
      logDroits('preselectionner → premier profil', { id: types[0].id, code: types[0].code });
      this.chargerDroitsPourProfil(types[0]);
    }
  }

  private chargerDroitsPourProfil(t: TypeProfilDto): void {
    const orgId = this.orgId();
    if (!orgId) {
      logDroits('chargerDroitsPourProfil : orgId manquant');
      return;
    }
    logDroits('GET droits → début', {
      orgId,
      typeProfilId: t.id,
      code: t.code,
      libelle: t.libelle,
    });
    this.droitsModal.set(t);
    this.droitsChargement.set(true);
    this.droitsLignes.set([]);
    this.typeProfilApi.listerDroits(orgId, t.id).subscribe({
      next: (lignes) => {
        this.droitsLignes.set(lignes);
        this.droitsSnapshotApi = Object.fromEntries(
          lignes.map((l) => [l.actionCode, l.niveau as NiveauDroitUi])
        );
        this.droitsChargement.set(false);
        logDroits('GET droits ← OK', {
          typeProfilId: t.id,
          resume: resumeNiveauxDroits(lignes),
          echantillon: lignes.slice(0, 3).map((l) => ({
            actionCode: l.actionCode,
            niveau: l.niveau,
            libelle: l.libelle,
          })),
        });
      },
      error: (err) => {
        this.droitsChargement.set(false);
        logDroits('GET droits ← ERREUR', {
          status: err?.status,
          message: err?.error?.message ?? err?.message,
          url: err?.url,
        });
        this.notify.error('Impossible de charger les droits.');
      },
    });
  }

  afficherEnteteSection(index: number): boolean {
    const lignes = this.droitsLignes();
    const cur = lignes[index]?.section;
    if (!cur) {
      return false;
    }
    if (index === 0) {
      return true;
    }
    return cur !== lignes[index - 1]?.section;
  }

  sectionModuleConfigurable(section: string | null | undefined): boolean {
    return estSectionOrgConfigurable(section);
  }

  moduleSectionEstActif(section: string): boolean {
    return moduleSectionActif(this.droitsLignes(), section);
  }

  private rafraichirMesDroitsSession(orgId: number): void {
    if (!this.auth.compteBureau()) {
      return;
    }
    this.droitsAcces.chargerEtMemoriser(orgId).subscribe({
      next: (d) => this.droitsAcces.setDroits(d),
    });
  }

  basculerModuleSection(section: string, actif: boolean): void {
    this.droitsLignes.set(appliquerModuleSection(this.droitsLignes(), section, actif));
    logDroits('module section', { section, actif });
  }

  closeDroitsModal(): void {
    logDroits('fermeture panneau droits');
    this.droitsModal.set(null);
    this.droitsLignes.set([]);
    this.droitsSnapshotApi = {};
  }

  changerNiveauDroit(actionCode: string, niveau: NiveauDroitUi): void {
    if (!NIVEAUX_DROIT_OPTIONS.some((o) => o.value === niveau)) {
      logDroits('changement niveau ignoré (valeur invalide)', { actionCode, niveau });
      return;
    }
    const avant = this.droitsLignes().find((l) => l.actionCode === actionCode)?.niveau;
    if (avant === niveau) {
      logDroits('changement niveau : aucun écart (sélecteur)', { actionCode, niveau });
      return;
    }
    this.droitsLignes.update((lignes) =>
      lignes.map((l) => (l.actionCode === actionCode ? { ...l, niveau } : l))
    );
    logDroits('changement niveau', { actionCode, avant, apres: niveau });
  }

  private listerModificationsDroits(
    droits: { actionCode: string; niveau: NiveauDroitApi }[]
  ): { actionCode: string; avant: NiveauDroitUi | undefined; apres: NiveauDroitApi }[] {
    return droits
      .filter((d) => this.droitsSnapshotApi[d.actionCode] !== d.niveau)
      .map((d) => ({
        actionCode: d.actionCode,
        avant: this.droitsSnapshotApi[d.actionCode],
        apres: d.niveau,
      }));
  }

  enregistrerDroits(): void {
    const orgId = this.orgId();
    const t = this.droitsModal();
    if (!orgId || !t) {
      logDroits('enregistrer : annulé (orgId ou profil manquant)', { orgId, profilId: t?.id });
      return;
    }
    if (this.droitsChargement() || this.droitsLignes().length === 0) {
      logDroits('enregistrer : annulé (pas prêt)', {
        chargement: this.droitsChargement(),
        lignes: this.droitsLignes().length,
      });
      this.notify.error('Attendez le chargement complet des actions avant d\'enregistrer.');
      return;
    }
    const droits = this.droitsLignes().map((l) => ({
      actionCode: l.actionCode,
      niveau: l.niveau as NiveauDroitApi,
    }));
    const modifications = this.listerModificationsDroits(droits);
    const url = `${orgId}/types-profil/${t.id}/droits`;
    logDroits('PUT droits → début', {
      url,
      typeProfilId: t.id,
      code: t.code,
      resume: resumeNiveauxDroits(droits),
      nbModifications: modifications.length,
      modifications,
    });
    if (modifications.length === 0) {
      logDroits('PUT droits : aucune modification par rapport au chargement API');
      this.notify.info('Aucune modification à enregistrer.');
      return;
    }
    this.savingDroits.set(true);
    this.typeProfilApi.sauvegarderDroits(orgId, t.id, droits).subscribe({
      next: (lignes) => {
        this.savingDroits.set(false);
        this.droitsLignes.set([...lignes]);
        const persistees = modifications.map((m) => ({
          actionCode: m.actionCode,
          demande: m.apres,
          serveur: lignes.find((l) => l.actionCode === m.actionCode)?.niveau,
        }));
        this.droitsSnapshotApi = Object.fromEntries(
          lignes.map((l) => [l.actionCode, l.niveau as NiveauDroitUi])
        );
        logDroits('PUT droits ← OK', {
          typeProfilId: t.id,
          resume: resumeNiveauxDroits(lignes),
          lignesCount: lignes.length,
          persistees,
        });
        setTimeout(() => this.notify.success('Droits enregistrés pour ce profil.'), 0);
        this.rafraichirMesDroitsSession(orgId);
      },
      error: (err) => {
        this.savingDroits.set(false);
        logDroits('PUT droits ← ERREUR', {
          status: err?.status,
          statusText: err?.statusText,
          message: err?.error?.message ?? err?.message,
          body: err?.error,
          url: err?.url,
        });
        this.notify.error(err?.error?.message ?? 'Échec de l\'enregistrement des droits.');
      },
    });
  }

  enregistrer(): void {
    if (this.editingUser()) {
      this.notify.info('La modification complète du profil sera disponible prochainement');
      this.closeModal();
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const orgId = this.orgId();
    if (orgId == null) return;
    const v = this.form.getRawValue();
    const role = roleApiDepuisFormUi(this.roleFormUi());
    if (this.roleFormUi() === 'MEMBRE_SIMPLE' && v.membreId == null) {
      this.notify.error('Sélectionnez un membre à lier au compte');
      return;
    }
    if (this.roleFormUi() === 'MEMBRE_BUREAU' && v.typeProfilId == null) {
      this.notify.error('Choisissez un profil applicatif (Secrétaire général, Trésorier, etc.)');
      return;
    }
    const body: CreateUtilisateurOrgBody = {
      prenom: v.prenom.trim(),
      nom: v.nom.trim(),
      email: v.email.trim(),
      role,
      compteActif: v.compteActif,
      poste: v.poste,
      membreId:
        role === 'MEMBRE' && this.roleFormUi() === 'MEMBRE_SIMPLE'
          ? v.membreId ?? undefined
          : undefined,
      typeProfilId: v.typeProfilId ?? undefined,
      envoyerEmailActivation: role === 'MEMBRE' ? v.envoyerActivation : undefined,
    };
    this.saving.set(true);
    this.accesService.creer(orgId, body).subscribe({
      next: () => {
        this.saving.set(false);
        this.closeModal();
        const msgBureau =
          this.roleFormUi() === 'MEMBRE_BUREAU'
            ? v.envoyerActivation
              ? 'Compte bureau créé · Connexion par email · mot de passe Passer123 envoyé'
              : 'Compte bureau créé · Connexion par email · mot de passe initial : Passer123'
            : role === 'MEMBRE' && v.envoyerActivation
              ? 'Compte membre créé · Mot de passe initial Passer123 envoyé par email'
              : role === 'MEMBRE'
                ? 'Compte membre créé · Mot de passe initial : Passer123'
                : 'Utilisateur créé';
        this.notify.success(msgBureau);
        this.charger();
      },
      error: (err) => {
        this.saving.set(false);
        this.notify.error(err?.error?.message ?? 'Création impossible');
      },
    });
  }

  basculerActif(u: UtilisateurOrgDto): void {
    const orgId = this.orgId();
    if (orgId == null) return;
    const next = !u.actif;
    this.accesService.basculerActif(orgId, u.utilisateurId, next).subscribe({
      next: (updated) => {
        this.users.update((list) =>
          list.map((x) => (x.utilisateurId === updated.utilisateurId ? updated : x))
        );
        this.notify.success(next ? 'Compte réactivé' : 'Compte suspendu');
        this.accesService.stats(orgId).subscribe({ next: (s) => this.stats.set(s) });
      },
      error: (err) => this.notify.error(err?.error?.message ?? 'Action impossible'),
    });
  }

  toggleAcces(u: UtilisateurOrgDto, event: Event): void {
    event.preventDefault();
    this.basculerActif(u);
  }

  resetMdp(): void {
    this.notify.info('Email de réinitialisation envoyé (démo)');
  }

  confirmResetAll(): void {
    this.notify.info('Action requiert confirmation admin');
  }

  onMembreChange(): void {
    const id = this.form.controls.membreId.value;
    const m = this.membresSansCompte().find((x) => x.id === id);
    if (m) {
      this.form.patchValue({
        prenom: m.prenom,
        nom: m.nom,
        email: m.email ?? '',
        poste: m.poste ?? 'SIMPLE',
      });
    }
    this.proposerTypeProfilPourPoste();
  }

  onPosteUtilisateurChange(): void {
    this.proposerTypeProfilPourPoste();
  }
}
