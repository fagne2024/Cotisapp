import { NgStyle } from '@angular/common';
import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SuperadminContextService } from '../../core/services/superadmin-context.service';
import {
  CreateOrganisationBody,
  OrganisationService,
} from '../../core/services/organisation.service';
import { CreateCompteModeleMembreBody } from '../../core/services/compte-modele-membre.service';
import {
  OrganisationResumeDto,
  SuperadminService,
  SuperadminVueGlobaleDto,
} from '../../core/services/superadmin.service';
import { NotificationService } from '../../core/services/notification.service';
import { formatFcfa } from '../../core/utils/currency.util';

type ModalKind = 'create' | 'edit' | 'delete' | 'resetMdp' | 'reset2fa';

@Component({
  selector: 'app-superadmin-dashboard',
  standalone: true,
  imports: [ReactiveFormsModule, NgStyle, RouterLink],
  templateUrl: './superadmin.component.html',
  styleUrl: './superadmin.component.scss',
})
export class SuperadminComponent implements OnInit, OnDestroy {
  private readonly orgService = inject(OrganisationService);
  private readonly superadminService = inject(SuperadminService);
  private readonly fb = inject(FormBuilder);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly saContext = inject(SuperadminContextService);

  readonly formatFcfa = formatFcfa;
  /** Affichage template (évite le caractère @ interprété comme bloc Angular). */
  readonly mdpDefautLibelle = 'Admin@2026';
  readonly vue = signal<SuperadminVueGlobaleDto | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal('');
  readonly createOpen = signal(false);
  readonly editOpen = signal(false);
  readonly deleteTarget = signal<OrganisationResumeDto | null>(null);
  readonly resetMdpTarget = signal<OrganisationResumeDto | null>(null);
  readonly reset2faTarget = signal<OrganisationResumeDto | null>(null);
  readonly editingOrg = signal<OrganisationResumeDto | null>(null);
  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly modelesDraft = signal<CreateCompteModeleMembreBody[]>([]);
  readonly logoBlobUrls = signal<Record<number, string>>({});
  readonly logoFichier = signal<File | null>(null);
  readonly logoApercuLocal = signal<string | null>(null);
  readonly uploadingLogo = signal(false);
  readonly filteredOrgs = computed(() => this.filterOrganisations(this.vue()?.organisations ?? []));

  /** Liste des organisations avec filtre recherche (vue admins GIE). */
  readonly filteredAdminsGie = computed(() => this.filterOrganisations(this.vue()?.organisations ?? []));

  private filterOrganisations(list: OrganisationResumeDto[]): OrganisationResumeDto[] {
    const q = this.searchQuery().trim().toLowerCase();
    if (!q) return list;
    return list.filter(
      (o) =>
        o.nom.toLowerCase().includes(q) ||
        o.code.toLowerCase().includes(q) ||
        this.adminLibelle(o).toLowerCase().includes(q) ||
        o.adminEmail.toLowerCase().includes(q)
    );
  }

  readonly chartMax = computed(() => {
    const bars = this.vue()?.cotisationsParOrganisation ?? [];
    return Math.max(...bars.map((b) => b.montant), 1);
  });

  readonly createForm = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(50)]],
    nom: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    adminPrenom: ['', [Validators.required, Validators.maxLength(100)]],
    adminNom: ['', [Validators.required, Validators.maxLength(100)]],
    adminEmail: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    adminMotDePasse: ['', [Validators.minLength(8)]],
    solidarite: [true],
    banque: [false],
    epargneHebdo: [true],
    epargneMois: [true],
    penalite: [false],
    amende: [false],
    nouveauModele: this.fb.group({
      code: [''],
      libelle: [''],
    }),
  });

  readonly editForm = this.fb.group({
    nom: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    actif: [true],
    adminPrenom: ['', [Validators.required, Validators.maxLength(100)]],
    adminNom: ['', [Validators.required, Validators.maxLength(100)]],
    adminEmail: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    adminMotDePasse: ['', [Validators.minLength(8)]],
    adminActif: [true],
  });

  readonly resetMdpForm = this.fb.group({
    motDePasse: ['', [Validators.minLength(8)]],
    forcerChangement: [false],
  });

  ngOnInit(): void {
    this.loadData();
  }

  adminLibelle(org: OrganisationResumeDto): string {
    if (!org.adminUtilisateurId) return '—';
    const p = (org.adminPrenom ?? '').trim();
    const n = (org.adminNom ?? '').trim();
    if (p && n && n !== '—') return `${p} ${n}`;
    return n && n !== '—' ? n : p || '—';
  }

  ngOnDestroy(): void {
    this.revoquerTousLesLogos();
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey) {
      if (event.key === 'n' || event.key === 'N') {
        event.preventDefault();
        this.createOpen.set(true);
      } else if (event.key === 'f' || event.key === 'F') {
        event.preventDefault();
        // Focus on search input if available
        const searchInput = document.querySelector('input[type="search"]') as HTMLInputElement;
        if (searchInput) searchInput.focus();
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.createOpen.set(false);
      this.editOpen.set(false);
      this.searchQuery.set('');
    }
  }

  logoAffiche(org: OrganisationResumeDto): string | null {
    const local = this.logoApercuLocal();
    const editing = this.editingOrg();
    if (editing?.id === org.id && local) {
      return local;
    }
    return this.logoBlobUrls()[org.id] ?? null;
  }

  logoAfficheEdition(): string | null {
    return this.logoApercuLocal() ?? (this.editingOrg() ? this.logoBlobUrls()[this.editingOrg()!.id] ?? null : null);
  }

  onLogoSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      this.formError.set('Le logo ne doit pas dépasser 2 Mo.');
      input.value = '';
      return;
    }
    const ok = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'].includes(file.type);
    if (!ok) {
      this.formError.set('Format accepté : PNG, JPEG, WebP ou SVG.');
      input.value = '';
      return;
    }
    this.formError.set(null);
    this.logoFichier.set(file);
    const prev = this.logoApercuLocal();
    if (prev?.startsWith('blob:')) {
      URL.revokeObjectURL(prev);
    }
    this.logoApercuLocal.set(URL.createObjectURL(file));
    input.value = '';
  }

  retirerLogoSelection(): void {
    this.logoFichier.set(null);
    const prev = this.logoApercuLocal();
    if (prev?.startsWith('blob:')) {
      URL.revokeObjectURL(prev);
    }
    this.logoApercuLocal.set(null);
  }

  private chargerLogosOrganisations(orgs: OrganisationResumeDto[]): void {
    for (const url of Object.values(this.logoBlobUrls())) {
      if (url.startsWith('blob:')) {
        URL.revokeObjectURL(url);
      }
    }
    this.logoBlobUrls.set({});
    for (const org of orgs) {
      if (!org.logoUrl) {
        continue;
      }
      this.orgService.telechargerLogo(org.id).subscribe({
        next: (blob) => {
          this.logoBlobUrls.update((m) => ({ ...m, [org.id]: URL.createObjectURL(blob) }));
        },
        error: () => {
          /* ignore : affichage initiales */
        },
      });
    }
  }

  private revoquerTousLesLogos(): void {
    for (const url of Object.values(this.logoBlobUrls())) {
      if (url.startsWith('blob:')) {
        URL.revokeObjectURL(url);
      }
    }
    this.logoBlobUrls.set({});
    const local = this.logoApercuLocal();
    if (local?.startsWith('blob:')) {
      URL.revokeObjectURL(local);
    }
    this.logoApercuLocal.set(null);
  }

  private reinitialiserLogoFormulaire(): void {
    this.logoFichier.set(null);
    const local = this.logoApercuLocal();
    if (local?.startsWith('blob:')) {
      URL.revokeObjectURL(local);
    }
    this.logoApercuLocal.set(null);
  }

  private uploadLogoSiBesoin(orgId: number, onDone: () => void): void {
    const file = this.logoFichier();
    if (!file) {
      onDone();
      return;
    }
    this.uploadingLogo.set(true);
    this.orgService.uploadLogo(orgId, file).subscribe({
      next: () => {
        this.uploadingLogo.set(false);
        this.reinitialiserLogoFormulaire();
        onDone();
      },
      error: (err) => {
        this.uploadingLogo.set(false);
        this.handleApiError(err, 'Organisation enregistrée, mais échec de l\'envoi du logo.');
        onDone();
      },
    });
  }

  formatCompact(amount: number): string {
    if (amount >= 1_000_000) {
      const m = amount / 1_000_000;
      return `${m % 1 === 0 ? m.toFixed(0) : m.toFixed(1).replace('.', ',')}M`;
    }
    if (amount >= 1_000) {
      const k = amount / 1_000;
      return `${k % 1 === 0 ? k.toFixed(0) : k.toFixed(1).replace('.', ',')}k`;
    }
    return formatFcfa(amount).replace(' F', '');
  }

  barHeight(montant: number): number {
    const max = this.chartMax();
    return Math.max(8, Math.round((montant / max) * 100));
  }

  barColor(index: number): string {
    const i = index % 3;
    if (i === 0) return 'linear-gradient(180deg, var(--g2), var(--g3))';
    if (i === 1) return 'linear-gradient(180deg, var(--pu), var(--pu2))';
    return 'linear-gradient(180deg, var(--lt), #ddd)';
  }

  barValueColor(index: number): string {
    const i = index % 3;
    if (i === 0) return 'var(--g1)';
    if (i === 1) return 'var(--pu)';
    return 'var(--lt)';
  }

  orgLogoStyle(index: number): { background: string } {
    const i = index % 3;
    if (i === 0) return { background: 'var(--g1)' };
    if (i === 1) return { background: 'var(--pu)' };
    return { background: 'var(--lt)' };
  }

  onSearch(event: Event): void {
    this.searchQuery.set((event.target as HTMLInputElement).value);
  }

  selectedComptesRecap(): { label: string; warn?: boolean; muted?: boolean }[] {
    const v = this.createForm.getRawValue();
    const tags: { label: string; warn?: boolean; muted?: boolean }[] = [
      { label: '💵 Caisse', muted: true },
      { label: '📈 Compte intérêts', muted: true },
    ];
    if (v.solidarite) tags.push({ label: '🤝 Solidarité' });
    if (v.banque) tags.push({ label: '🏦 Banque' });
    if (v.epargneHebdo) tags.push({ label: '📅 Épargne hebdo' });
    if (v.epargneMois) tags.push({ label: '📆 Épargne mois' });
    if (v.penalite) tags.push({ label: '⚠ Pénalité', warn: true });
    if (v.amende) tags.push({ label: '🚫 Amende', warn: true });
    for (const m of this.modelesDraft()) {
      tags.push({ label: `🏷 ${m.libelle}` });
    }
    return tags;
  }

  gererOrg(org: OrganisationResumeDto): void {
    this.saContext.selectOrg({ id: org.id, nom: org.nom, code: org.code });
    void this.router.navigate(['/superadmin', 'org', org.id, 'dashboard']);
  }

  loadData(): void {
    this.loading.set(true);
    this.error.set(null);
    this.superadminService.vueGlobale().subscribe({
      next: (data) => {
        this.vue.set(data);
        this.chargerLogosOrganisations(data.organisations);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger la vue superadmin.');
        this.loading.set(false);
      },
    });
  }

  openCreateModal(): void {
    this.formError.set(null);
    this.modelesDraft.set([]);
    this.createForm.reset({
      code: '',
      nom: '',
      description: '',
      adminPrenom: '',
      adminNom: '',
      adminEmail: '',
      adminMotDePasse: '',
      solidarite: true,
      banque: false,
      epargneHebdo: true,
      epargneMois: true,
      penalite: false,
      amende: false,
      nouveauModele: { code: '', libelle: '' },
    });
    this.reinitialiserLogoFormulaire();
    this.createOpen.set(true);
  }

  closeCreateModal(): void {
    if (this.saving()) return;
    this.createOpen.set(false);
    this.formError.set(null);
    this.reinitialiserLogoFormulaire();
  }

  openEditModal(org: OrganisationResumeDto): void {
    this.formError.set(null);
    this.editingOrg.set(org);
    const { prenom, nom } = this.adminNomsDepuisOrg(org);
    this.editForm.reset({
      nom: org.nom,
      description: org.description ?? '',
      actif: org.actif,
      adminPrenom: prenom,
      adminNom: nom,
      adminEmail: org.adminEmail !== '—' ? org.adminEmail : '',
      adminMotDePasse: '',
      adminActif: org.adminActif !== false,
    });
    this.reinitialiserLogoFormulaire();
    this.editOpen.set(true);
  }

  closeEditModal(): void {
    if (this.saving()) return;
    this.editOpen.set(false);
    this.editingOrg.set(null);
    this.formError.set(null);
  }

  openResetMdpModal(org: OrganisationResumeDto): void {
    if (!org.adminUtilisateurId) {
      this.notify.info('Aucun administrateur GIE : ouvrez « Modifier » pour en créer un.');
      this.openEditModal(org);
      return;
    }
    this.formError.set(null);
    this.resetMdpTarget.set(org);
    this.resetMdpForm.reset({
      motDePasse: '',
      forcerChangement: false,
    });
  }

  closeResetMdpModal(): void {
    if (this.saving()) return;
    this.resetMdpTarget.set(null);
    this.formError.set(null);
  }

  appliquerMdpParDefaut(): void {
    this.resetMdpForm.patchValue({ motDePasse: 'Admin@2026' });
  }

  submitResetMdp(): void {
    const org = this.resetMdpTarget();
    if (!org || this.resetMdpForm.invalid) {
      this.resetMdpForm.markAllAsTouched();
      return;
    }
    const v = this.resetMdpForm.getRawValue();
    const pwd = (v.motDePasse ?? '').trim();
    this.saving.set(true);
    this.formError.set(null);
    this.superadminService
      .reinitialiserMotDePasseAdminGie(org.id, {
        motDePasse: pwd || undefined,
        forcerChangement: !!v.forcerChangement,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.resetMdpTarget.set(null);
          this.loadData();
          this.showToast(`Mot de passe administrateur mis à jour pour « ${org.nom} ».`);
        },
        error: (err) => this.handleApiError(err, 'Impossible de réinitialiser le mot de passe.'),
      });
  }

  openReset2faModal(org: OrganisationResumeDto): void {
    if (!org.adminUtilisateurId) {
      this.notify.info('Aucun administrateur GIE : ouvrez « Modifier » pour en créer un.');
      this.openEditModal(org);
      return;
    }
    this.formError.set(null);
    this.reset2faTarget.set(org);
  }

  closeReset2faModal(): void {
    if (this.saving()) return;
    this.reset2faTarget.set(null);
    this.formError.set(null);
  }

  submitReset2fa(): void {
    const org = this.reset2faTarget();
    if (!org) return;
    this.saving.set(true);
    this.formError.set(null);
    this.superadminService.reinitialiserTwoFactorAdminGie(org.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.reset2faTarget.set(null);
        this.loadData();
        this.showToast(
          `Google Authenticator réinitialisé pour l'admin de « ${org.nom} ». Il devra le reconfigurer à la prochaine connexion.`
        );
      },
      error: (err) =>
        this.handleApiError(err, 'Impossible de réinitialiser Google Authenticator.'),
    });
  }

  confirmDelete(org: OrganisationResumeDto): void {
    this.formError.set(null);
    this.deleteTarget.set(org);
  }

  closeDeleteConfirm(): void {
    if (this.saving()) return;
    this.deleteTarget.set(null);
    this.formError.set(null);
  }

  toggleActif(org: OrganisationResumeDto): void {
    this.saving.set(true);
    this.orgService.modifier(org.id, { nom: org.nom, description: org.description ?? undefined, actif: !org.actif }).subscribe({
      next: () => {
        this.saving.set(false);
        this.loadData();
        this.showToast(org.actif ? `« ${org.nom} » désactivée.` : `« ${org.nom} » réactivée.`);
      },
      error: (err) => this.handleApiError(err, 'Impossible de modifier le statut.'),
    });
  }

  overlayClick(event: MouseEvent, kind: ModalKind): void {
    if ((event.target as HTMLElement).classList.contains('modal-overlay')) {
      if (kind === 'create') this.closeCreateModal();
      else if (kind === 'edit') this.closeEditModal();
      else if (kind === 'resetMdp') this.closeResetMdpModal();
      else if (kind === 'reset2fa') this.closeReset2faModal();
      else this.closeDeleteConfirm();
    }
  }

  ajouterModeleDraft(): void {
    const g = this.createForm.controls.nouveauModele;
    const code = (g.controls.code.value ?? '').trim().toUpperCase();
    const libelle = (g.controls.libelle.value ?? '').trim();
    if (!code || !libelle) {
      this.formError.set('Code et libellé requis pour un compte personnalisé.');
      return;
    }
    if (!/^[A-Z0-9_]+$/.test(code)) {
      this.formError.set('Code en majuscules, chiffres et underscore uniquement.');
      return;
    }
    if (this.modelesDraft().some((m) => m.code === code)) {
      this.formError.set('Ce code de compte existe déjà dans la liste.');
      return;
    }
    this.modelesDraft.update((list) => [...list, { code, libelle }]);
    g.reset({ code: '', libelle: '' });
    this.formError.set(null);
  }

  removeModeleDraft(code: string): void {
    this.modelesDraft.update((list) => list.filter((m) => m.code !== code));
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    const v = this.createForm.getRawValue();
    const body: CreateOrganisationBody = {
      code: (v.code ?? '').trim().toUpperCase(),
      nom: (v.nom ?? '').trim(),
      description: (v.description ?? '').trim() || undefined,
      comptes: {
        solidarite: !!v.solidarite,
        banque: !!v.banque,
        epargneHebdo: !!v.epargneHebdo,
        epargneMois: !!v.epargneMois,
        penalite: !!v.penalite,
        amende: !!v.amende,
      },
      modelesComptePersonnalises: this.modelesDraft(),
      administrateurGie: {
        prenom: (v.adminPrenom ?? '').trim(),
        nom: (v.adminNom ?? '').trim(),
        email: (v.adminEmail ?? '').trim().toLowerCase(),
        motDePasse: (v.adminMotDePasse ?? '').trim() || undefined,
      },
    };
    this.saving.set(true);
    this.formError.set(null);
    this.orgService.creer(body).subscribe({
      next: (created) => {
        this.uploadLogoSiBesoin(created.id, () => {
          this.saving.set(false);
          this.createOpen.set(false);
          this.loadData();
          this.showToast('Organisation et administrateur GIE créés.');
        });
      },
      error: (err) => this.handleApiError(err, 'Erreur lors de la création.'),
    });
  }

  submitEdit(): void {
    const org = this.editingOrg();
    if (!org || this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const v = this.editForm.getRawValue();
    this.saving.set(true);
    this.formError.set(null);
    this.orgService
      .modifier(org.id, {
        nom: (v.nom ?? '').trim(),
        description: (v.description ?? '').trim() || undefined,
        actif: !!v.actif,
      })
      .subscribe({
        next: () => {
          this.orgService
            .enregistrerAdminGie(org.id, {
              prenom: (v.adminPrenom ?? '').trim(),
              nom: (v.adminNom ?? '').trim(),
              email: (v.adminEmail ?? '').trim().toLowerCase(),
              motDePasse: (v.adminMotDePasse ?? '').trim() || undefined,
              forcerChangementMotDePasse: false,
              compteActif: !!v.adminActif,
            })
            .subscribe({
              next: () => {
                this.uploadLogoSiBesoin(org.id, () => {
                  this.saving.set(false);
                  this.editOpen.set(false);
                  this.editingOrg.set(null);
                  this.loadData();
                  this.showToast('Organisation et administrateur GIE mis à jour.');
                });
              },
              error: (err) => this.handleApiError(err, 'Erreur lors de la mise à jour de l’admin GIE.'),
            });
        },
        error: (err) => this.handleApiError(err, 'Erreur lors de la modification.'),
      });
  }

  private adminNomsDepuisOrg(org: OrganisationResumeDto): { prenom: string; nom: string } {
    if (org.adminPrenom && org.adminNom && org.adminNom !== '—') {
      return { prenom: org.adminPrenom, nom: org.adminNom };
    }
    if (org.adminNom && org.adminNom !== '—') {
      const parts = org.adminNom.trim().split(/\s+/);
      if (parts.length >= 2) {
        return { prenom: parts[0], nom: parts.slice(1).join(' ') };
      }
      return { prenom: parts[0], nom: '' };
    }
    return { prenom: '', nom: '' };
  }

  submitDelete(): void {
    const org = this.deleteTarget();
    if (!org) return;
    this.saving.set(true);
    this.formError.set(null);
    this.orgService.supprimer(org.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.deleteTarget.set(null);
        this.loadData();
        this.showToast(`Organisation « ${org.nom} » supprimée.`);
      },
      error: (err) => this.handleApiError(err, 'Erreur lors de la suppression.'),
    });
  }

  private handleApiError(err: unknown, fallback: string): void {
    this.saving.set(false);
    const body = (err as { error?: { message?: string } | string })?.error;
    let msg = fallback;
    if (typeof body === 'string') {
      msg = body;
    } else if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string') {
      msg = body.message;
    }
    this.formError.set(msg);
  }

  private showToast(msg: string): void {
    this.notify.show(msg);
  }
}
