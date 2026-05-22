import { Component, computed, inject, OnInit, signal, HostListener } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { ProfilDto, ProfilService } from '../../core/services/profil.service';
import { TwoFactorService, TwoFactorSetup } from '../../core/services/two-factor.service';
import { evaluerForceMotDePasse, initialsFromNom, ProfilActiviteUi, ProfilSessionUi } from './mon-profil.util';

@Component({
  selector: 'app-mon-profil',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './mon-profil.component.html',
  styleUrl: './mon-profil.component.scss',
})
export class MonProfilComponent implements OnInit {
  readonly Math = Math;
  readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly profilApi = inject(ProfilService);
  private readonly notify = inject(NotificationService);
  private readonly twoFactorApi = inject(TwoFactorService);

  readonly loading = signal(true);
  readonly savingInfo = signal(false);
  readonly savingPwd = signal(false);
  readonly profil = signal<ProfilDto | null>(null);

  readonly sessions = signal<ProfilSessionUi[]>([
    {
      id: 'current',
      icon: '💻',
      iconBg: 'var(--g3)',
      device: 'Session actuelle',
      meta: 'Navigateur web · CotisApp',
      current: true,
      online: true,
    },
  ]);
  readonly activite = signal<ProfilActiviteUi[]>([]);

  readonly twoFa = signal(false);
  readonly twoFaLoading = signal(false);
  readonly twoFaPanel = signal<'none' | 'setup' | 'disable'>('none');
  readonly twoFaSetup = signal<TwoFactorSetup | null>(null);
  readonly twoFaCode = signal('');
  readonly twoFaDisablePwd = signal('');
  readonly twoFaDisableCode = signal('');
  readonly alertesConnexion = signal(true);
  readonly expirationSession = signal(true);
  readonly notifEmail = signal(true);
  readonly notifPush = signal(true);
  readonly notifRapport = signal(true);
  readonly notifEmprunts = signal(true);
  readonly notifCotisations = signal(false);

  readonly pwdVisible = signal({ current: false, next: false, confirm: false });
  readonly pwdStrengthInput = signal('');

  readonly formInfo = this.fb.nonNullable.group({
    prenom: ['', Validators.required],
    nom: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    telephone: [''],
    telephoneSecondaire: [''],
    adresse: [''],
  });

  readonly formPwd = this.fb.nonNullable.group({
    motDePasseActuel: ['', Validators.required],
    nouveauMotDePasse: ['', [Validators.required, Validators.minLength(8)]],
    confirmationMotDePasse: ['', Validators.required],
  });

  readonly initials = computed(() => {
    const p = this.profil();
    if (!p) {
      return '?';
    }
    return initialsFromNom(p.prenom, p.nom);
  });

  readonly membreDepuisLabel = computed(() => {
    const d = this.profil()?.dateAdhesion ?? this.profil()?.dateCreation;
    if (!d) {
      return '';
    }
    const date = new Date(d);
    return new Intl.DateTimeFormat('fr-FR', { month: 'short', year: 'numeric' }).format(date);
  });

  readonly connexionParEmail = computed(() => this.profil()?.canalConnexion !== 'TELEPHONE');

  readonly emailLectureSeule = computed(() => this.profil()?.role === 'MEMBRE');

  readonly isAdmin = computed(() => {
    const role = this.profil()?.role ?? this.auth.currentRole();
    return role === 'SUPERADMIN' || role === 'ADMIN_GIE';
  });

  readonly twoFaObligatoire = computed(() => this.isAdmin());

  readonly scoreSecurite = computed(() => {
    let score = 40;
    if (this.alertesConnexion()) score += 15;
    if (this.expirationSession()) score += 15;
    if (this.twoFa()) score += 30;
    return Math.min(score, 100);
  });

  readonly pwdStrength = computed(() => evaluerForceMotDePasse(this.pwdStrengthInput()));

  readonly rapportLink = computed(() => {
    const orgId = this.auth.currentOrgId();
    const membreId = this.auth.currentMembreId();
    if (!orgId) {
      return null;
    }
    if (this.auth.currentRole() === 'MEMBRE' && membreId) {
      return ['/organisations', orgId, 'mon-compte', 'rapport'];
    }
    if (membreId) {
      return ['/organisations', orgId, 'membres', membreId, 'rapport'];
    }
    return ['/organisations', orgId, 'rapports'];
  });

  ngOnInit(): void {
    this.chargerProfil();
  }

  chargerProfil(): void {
    this.loading.set(true);
    this.profilApi.charger().subscribe({
      next: (p) => {
        this.profil.set(p);
        this.twoFa.set(!!p.twoFactorEnabled);
        this.formInfo.patchValue({
          prenom: p.prenom,
          nom: p.nom,
          email: p.email,
          telephone: p.telephone ?? '',
          telephoneSecondaire: p.telephoneSecondaire ?? '',
          adresse: p.adresse ?? '',
        });
        this.loading.set(false);
        this.chargerActivite();
      },
      error: (err) => {
        this.loading.set(false);
        this.notify.show(err?.error?.message ?? 'Impossible de charger le profil.');
      },
    });
  }

  private chargerActivite(): void {
    this.profilApi.activite().subscribe({
      next: (rows) => {
        this.activite.set(
          rows.map((a) => ({
            icon: iconeActivite(a.action),
            iconBg: fondActivite(a.action),
            title: a.libelle,
            date: formaterDateActivite(a.dateCreation),
          }))
        );
      },
      error: () => this.activite.set([]),
    });
  }

  annulerInfos(): void {
    const p = this.profil();
    if (!p) {
      return;
    }
    this.formInfo.patchValue({
      prenom: p.prenom,
      nom: p.nom,
      email: p.email,
      telephone: p.telephone ?? '',
      telephoneSecondaire: p.telephoneSecondaire ?? '',
      adresse: p.adresse ?? '',
    });
  }

  sauvegarderInfos(): void {
    if (this.formInfo.invalid) {
      this.formInfo.markAllAsTouched();
      return;
    }
    const raw = this.formInfo.getRawValue();
    this.savingInfo.set(true);
    this.profilApi
      .mettreAJour({
        prenom: raw.prenom.trim(),
        nom: raw.nom.trim(),
        email: raw.email.trim(),
        telephone: raw.telephone.trim() || undefined,
        telephoneSecondaire: raw.telephoneSecondaire.trim() || undefined,
        adresse: raw.adresse.trim() || undefined,
      })
      .subscribe({
        next: (p) => {
          this.profil.set(p);
          this.auth.mettreAJourSession({
            email: p.email,
            nomComplet: p.nomComplet,
            organisationNom: p.organisationNom,
            membreId: p.membreId,
          });
          this.chargerActivite();
          this.savingInfo.set(false);
          this.notify.show('Informations mises à jour.');
        },
        error: (err) => {
          this.savingInfo.set(false);
          this.notify.show(err?.error?.message ?? 'Échec de la mise à jour.');
        },
      });
  }

  annulerPwd(): void {
    this.formPwd.reset();
    this.pwdStrengthInput.set('');
  }

  onPwdNewInput(ev: Event): void {
    this.pwdStrengthInput.set((ev.target as HTMLInputElement).value);
  }

  togglePwdVisible(field: 'current' | 'next' | 'confirm'): void {
    this.pwdVisible.update((v) => ({ ...v, [field]: !v[field] }));
  }

  pwdInputType(field: 'current' | 'next' | 'confirm'): string {
    return this.pwdVisible()[field] ? 'text' : 'password';
  }

  changerMotDePasse(): void {
    if (this.formPwd.invalid) {
      this.formPwd.markAllAsTouched();
      return;
    }
    const raw = this.formPwd.getRawValue();
    if (raw.nouveauMotDePasse !== raw.confirmationMotDePasse) {
      this.notify.show('La confirmation ne correspond pas au nouveau mot de passe.');
      return;
    }
    this.savingPwd.set(true);
    this.profilApi
      .changerMotDePasse({
        motDePasseActuel: raw.motDePasseActuel,
        nouveauMotDePasse: raw.nouveauMotDePasse,
        confirmationMotDePasse: raw.confirmationMotDePasse,
      })
      .subscribe({
        next: () => {
          this.savingPwd.set(false);
          this.annulerPwd();
          this.notify.show('Mot de passe modifié avec succès.');
        },
        error: (err) => {
          this.savingPwd.set(false);
          this.notify.show(err?.error?.message ?? 'Impossible de modifier le mot de passe.');
        },
      });
  }

  fermerAutresSessions(): void {
    this.sessions.set(this.sessions().filter((s) => s.current));
    this.notify.show('Les autres sessions ont été fermées (démo).');
  }

  fermerSession(id: string): void {
    this.sessions.update((list) => list.filter((s) => s.id !== id));
    this.notify.show('Session fermée (démo).');
  }

  deconnecterTout(): void {
    this.notify.show('Déconnexion globale — utilisez Se déconnecter pour quitter cette session.');
  }

  exporterDonnees(): void {
    const p = this.profil();
    if (!p) {
      return;
    }
    const blob = new Blob([JSON.stringify(p, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `cotisapp-profil-${p.userId}.json`;
    a.click();
    URL.revokeObjectURL(url);
    this.notify.show('Export JSON téléchargé.');
  }

  sauvegarderPreferences(): void {
    this.notify.show('Préférences enregistrées (démo).');
  }

  modifierPhoto(): void {
    this.notify.show('Modification de la photo — bientôt disponible.');
  }

  activer2fa(): void {
    if (this.twoFa() && this.twoFaObligatoire()) {
      this.notify.show('La double authentification est obligatoire pour les comptes administrateur.');
      return;
    }
    if (this.twoFa()) {
      this.twoFaPanel.set('disable');
      this.twoFaDisablePwd.set('');
      this.twoFaDisableCode.set('');
      return;
    }
    this.twoFaLoading.set(true);
    this.twoFactorApi.setup().subscribe({
      next: (setup) => {
        this.twoFaSetup.set(setup);
        this.twoFaPanel.set('setup');
        this.twoFaCode.set('');
        this.twoFaLoading.set(false);
      },
      error: (err) => {
        this.twoFaLoading.set(false);
        this.notify.show(err?.error?.message ?? 'Impossible de démarrer la configuration 2FA.');
      },
    });
  }

  annuler2fa(): void {
    if (this.twoFaPanel() === 'setup' && !this.twoFa()) {
      this.twoFactorApi.annulerSetup().subscribe({ error: () => undefined });
    }
    this.twoFaPanel.set('none');
    this.twoFaSetup.set(null);
    this.twoFaCode.set('');
    this.twoFaDisablePwd.set('');
    this.twoFaDisableCode.set('');
  }

  onTwoFaCodeInput(ev: Event): void {
    const raw = (ev.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 6);
    this.twoFaCode.set(raw);
    (ev.target as HTMLInputElement).value = raw;
  }

  confirmer2fa(): void {
    const code = this.twoFaCode().trim();
    if (!/^\d{6}$/.test(code)) {
      this.notify.show('Saisissez le code à 6 chiffres de Google Authenticator.');
      return;
    }
    this.twoFaLoading.set(true);
    this.twoFactorApi.confirm(code).subscribe({
      next: (status) => {
        this.twoFa.set(status.enabled);
        this.twoFaPanel.set('none');
        this.twoFaSetup.set(null);
        this.twoFaLoading.set(false);
        this.notify.show('Double authentification activée.');
      },
      error: (err) => {
        this.twoFaLoading.set(false);
        this.notify.show(err?.error?.message ?? 'Code incorrect.');
      },
    });
  }

  onDisableCodeInput(ev: Event): void {
    const raw = (ev.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 6);
    this.twoFaDisableCode.set(raw);
    (ev.target as HTMLInputElement).value = raw;
  }

  desactiver2fa(): void {
    const pwd = this.twoFaDisablePwd().trim();
    const code = this.twoFaDisableCode().trim();
    if (!pwd || !/^\d{6}$/.test(code)) {
      this.notify.show('Mot de passe et code à 6 chiffres requis.');
      return;
    }
    this.twoFaLoading.set(true);
    this.twoFactorApi.disable(pwd, code).subscribe({
      next: (status) => {
        this.twoFa.set(status.enabled);
        this.twoFaPanel.set('none');
        this.twoFaLoading.set(false);
        this.notify.show('Double authentification désactivée.');
      },
      error: (err) => {
        this.twoFaLoading.set(false);
        this.notify.show(err?.error?.message ?? 'Impossible de désactiver la 2FA.');
      },
    });
  }

  strengthSegClass(index: number): string {
    const { score, level } = this.pwdStrength();
    if (index >= score || level === 'none') {
      return 'sb-seg';
    }
    return `sb-seg active ${level}`;
  }
}

function iconeActivite(action: string): string {
  if (action.includes('CONNEXION')) return '🔐';
  if (action.includes('PROFIL')) return '👤';
  if (action.includes('MOT_DE_PASSE')) return '🔑';
  if (action.includes('COTISATION')) return '💰';
  if (action.includes('DEPENSE')) return '📤';
  if (action.includes('EMPRUNT')) return '📋';
  return '•';
}

function fondActivite(action: string): string {
  if (action.includes('CONNEXION')) return 'var(--g3)';
  if (action.includes('MOT_DE_PASSE') || action.includes('PROFIL')) return 'var(--bl2)';
  return 'var(--or3)';
}

function formaterDateActivite(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const sameDay =
    d.getDate() === now.getDate() &&
    d.getMonth() === now.getMonth() &&
    d.getFullYear() === now.getFullYear();
  if (sameDay) {
    return "Aujourd'hui " + new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit' }).format(d);
  }
  return new Intl.DateTimeFormat('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(d);
}
