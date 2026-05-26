import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { CompteMembreLogin, Role } from '../../../core/models/role.model';
import {
  estSaisieEmailProbable,
  messageErreurIdentifiant,
  ModeIdentifiantLogin,
  resoutModeIdentifiant,
  validateursEmailLogin,
  validateursTelephoneLogin,
} from './login-identifiant.validators';
import {
  estConnexionEmail,
  estMembreBureau,
  estMembreSimple,
  groupePourPreset,
  libelleLoginPreset,
  LoginGroupe,
  LoginPreset,
  PRESETS_CONNEXION_EMAIL,
} from './login-preset.util';
type EtapeMembre = 'telephone' | 'choix' | 'motdepasse';
type EtapeConnexion = 'identifiants' | 'twoFactor';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  readonly Math = Math;
  readonly libelleLoginPreset = libelleLoginPreset;
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly error = signal(false);
  readonly errorMessage = signal('');
  readonly loading = signal(false);
  readonly lookupLoading = signal(false);
  readonly showPassword = signal(false);
  readonly modeIdentifiant = signal<ModeIdentifiantLogin>('vide');
  readonly presetsEmail = PRESETS_CONNEXION_EMAIL;
  readonly selectedGroupe = signal<LoginGroupe | null>(null);
  readonly selectedRole = signal<LoginPreset | null>(null);
  readonly roleRequis = signal(false);
  readonly etapeMembre = signal<EtapeMembre>('telephone');
  readonly comptesMembre = signal<CompteMembreLogin[]>([]);
  readonly compteSelectionne = signal<CompteMembreLogin | null>(null);
  readonly etapeConnexion = signal<EtapeConnexion>('identifiants');
  readonly twoFactorToken = signal('');
  readonly code2fa = signal('');

  readonly features = [
    { icon: '💰', title: 'Cotisations & Épargne', sub: 'Suivi hebdomadaire et mensuel automatisé' },
    { icon: '📋', title: 'Emprunts intelligents', sub: 'Étalé, Caisse, Solidarité — calcul auto des échéances' },
    { icon: '⚙', title: 'Règles personnalisables', sub: 'Chaque GIE configure ses propres règles comptables' },
    { icon: '📊', title: 'Rapports & Exports', sub: 'PDF et Excel générés en un clic' },
  ];

  readonly form = this.fb.nonNullable.group({
    identifiant: ['', Validators.required],
    motDePasse: [''],
  });

  readonly roleSelectionne = computed(() => this.selectedRole() != null);
  readonly modeTelephone = computed(() => this.modeIdentifiant() === 'telephone');
  readonly modeEmail = computed(() => this.modeIdentifiant() === 'email');

  readonly identifiantLabel = computed(() => {
    if (this.modeTelephone()) return 'Numéro de téléphone';
    if (this.modeEmail()) return 'Adresse email';
    return 'Téléphone ou email';
  });

  readonly identifiantPlaceholder = computed(() => {
    if (this.modeTelephone()) return '+221 77 000 00 00';
    if (this.modeEmail()) return 'votre.email@exemple.sn';
    return '+221 77 … ou admin@mon-gie.sn';
  });

  readonly identifiantInputType = computed(() =>
    this.modeTelephone() ? 'tel' : 'text'
  );

  readonly identifiantAutocomplete = computed(() =>
    this.modeTelephone() ? 'tel' : 'username'
  );

  readonly roleHint = computed(() => {
    const mode = this.modeIdentifiant();
    if (mode === 'vide') {
      return 'Saisissez votre numéro de téléphone (membre simple) ou votre email (bureau, admin, superadmin).';
    }
    if (mode === 'telephone') {
      return 'Connexion membre simple — numéro au format international (ex. +221 77 …).';
    }
    if (!this.roleSelectionne()) {
      return 'Choisissez votre type de compte ci-dessous pour continuer.';
    }
    const role = this.selectedRole();
    if (role === 'super') {
      return 'Accès plateforme : gestion de toutes les organisations.';
    }
    if (estMembreBureau(role)) {
      return 'Compte bureau (SG, trésorier, superviseur…).';
    }
    return 'Administrateur de votre organisation GIE.';
  });

  readonly erreurIdentifiant = computed(() => {
    const ctrl = this.form.controls.identifiant;
    if (!ctrl.touched && !ctrl.dirty) return null;
    return messageErreurIdentifiant(ctrl.errors, this.rolePourErreurIdentifiant());
  });

  readonly afficherChampsIdentifiants = computed(() => this.etapeConnexion() === 'identifiants');

  readonly afficherChoixRoleEmail = computed(() => {
    if (this.etapeConnexion() !== 'identifiants' || this.modeTelephone()) {
      return false;
    }
    const v = (this.form.controls.identifiant.value ?? '').trim();
    return this.modeEmail() || estSaisieEmailProbable(v);
  });

  readonly afficherMotDePasse = computed(() => {
    if (this.etapeConnexion() !== 'identifiants') return false;
    if (this.modeTelephone()) return this.etapeMembre() === 'motdepasse';
    return this.modeEmail() && this.roleSelectionne();
  });

  readonly afficherEtape2fa = computed(() => this.etapeConnexion() === 'twoFactor');

  readonly plusieursComptes = computed(() => this.comptesMembre().length > 1);

  /** Liste à cocher : étape dédiée ou changement sur l’écran mot de passe. */
  readonly afficherListeComptes = computed(() => {
    if (!this.modeTelephone() || this.comptesMembre().length <= 1) {
      return false;
    }
    return this.etapeMembre() === 'choix' || this.etapeMembre() === 'motdepasse';
  });

  readonly afficherCompteUnique = computed(
    () =>
      this.modeTelephone() &&
      this.comptesMembre().length === 1 &&
      this.etapeMembre() === 'motdepasse' &&
      this.compteSelectionne() != null
  );

  readonly afficherBoutonContinuer = computed(
    () =>
      this.modeTelephone() &&
      this.etapeConnexion() === 'identifiants' &&
      this.etapeMembre() === 'telephone'
  );

  readonly afficherBoutonContinuerChoix = computed(
    () =>
      this.modeTelephone() &&
      this.etapeConnexion() === 'identifiants' &&
      this.etapeMembre() === 'choix'
  );

  readonly afficherBoutonConnexion = computed(() => {
    if (this.etapeConnexion() !== 'identifiants') return false;
    if (this.modeTelephone()) return this.etapeMembre() === 'motdepasse';
    return this.modeEmail() && this.roleSelectionne();
  });

  readonly afficherBouton2fa = computed(() => this.etapeConnexion() === 'twoFactor');

  readonly identifiantRenseigne = computed(
    () => (this.form.controls.identifiant.value ?? '').trim().length > 0
  );

  /** Bouton principal toujours visible sur l’écran de connexion. */
  readonly afficherBoutonAction = computed(() => {
    if (this.afficherEtape2fa()) {
      return this.afficherBouton2fa();
    }
    return this.etapeConnexion() === 'identifiants';
  });

  /** Indication visuelle seulement — le bouton reste cliquable (validation au clic). */
  readonly boutonActionDesactive = computed(() => this.loading() || this.lookupLoading());

  readonly libelleBoutonPrincipal = computed(() => {
    if (this.loading()) return 'Connexion en cours…';
    if (this.lookupLoading()) return 'Recherche…';
    if (this.afficherBouton2fa()) return 'Valider le code →';
    if (this.afficherBoutonContinuer()) return 'Continuer →';
    if (this.afficherBoutonContinuerChoix()) return 'Continuer →';
    if (this.afficherBoutonConnexion()) return 'Se connecter →';
    if (this.modeEmail() && !this.roleSelectionne()) return 'Continuer →';
    if (this.identifiantRenseigne()) return 'Continuer →';
    return 'Continuer →';
  });

  readonly code2faValide = computed(() => /^\d{6}$/.test(this.code2fa().replace(/\s/g, '')));

  constructor() {
    this.form.controls.identifiant.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => this.appliquerModeDepuisSaisie(v ?? ''));
    const initial = this.form.controls.identifiant.value ?? '';
    if (initial.trim()) {
      this.appliquerModeDepuisSaisie(initial);
    }
    this.synchroniserValidateurMotDePasse();
  }

  setRole(role: LoginPreset): void {
    if (!PRESETS_CONNEXION_EMAIL.includes(role)) {
      return;
    }
    const identifiant = (this.form.controls.identifiant.value ?? '').trim();
    if (!estSaisieEmailProbable(identifiant)) {
      return;
    }
    this.modeIdentifiant.set('email');
    this.appliquerValidateursIdentifiant('email');
    this.selectedGroupe.set(groupePourPreset(role));
    this.selectedRole.set(role);
    this.roleRequis.set(false);
    this.error.set(false);
    this.errorMessage.set('');
    this.synchroniserValidateurMotDePasse();
  }

  isRoleActive(role: LoginPreset): boolean {
    return this.selectedRole() === role;
  }

  togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  trackCompte(c: CompteMembreLogin): string {
    return `${c.organisationId}-${c.membreId}`;
  }

  estCompteSelectionne(c: CompteMembreLogin): boolean {
    const sel = this.compteSelectionne();
    return sel != null && sel.membreId === c.membreId && sel.organisationId === c.organisationId;
  }

  selectionnerCompte(compte: CompteMembreLogin): void {
    this.compteSelectionne.set(compte);
    this.error.set(false);
    this.errorMessage.set('');
  }

  confirmerChoixCompte(): void {
    if (this.compteSelectionne() == null) {
      this.error.set(true);
      this.errorMessage.set('Sélectionnez le compte membre à ouvrir.');
      return;
    }
    this.etapeMembre.set('motdepasse');
    this.error.set(false);
    this.errorMessage.set('');
    this.synchroniserValidateurMotDePasse();
  }

  retourChoixCompte(): void {
    if (this.plusieursComptes()) {
      this.etapeMembre.set('choix');
    } else {
      this.etapeMembre.set('telephone');
    }
    this.compteSelectionne.set(null);
    this.form.controls.motDePasse.setValue('');
    this.synchroniserValidateurMotDePasse();
  }

  retourTelephone(): void {
    this.etapeMembre.set('telephone');
    this.comptesMembre.set([]);
    this.compteSelectionne.set(null);
    this.form.controls.motDePasse.setValue('');
    this.error.set(false);
    this.errorMessage.set('');
    this.synchroniserValidateurMotDePasse();
  }

  submit(): void {
    if (this.loading() || this.lookupLoading()) {
      return;
    }
    if (this.etapeConnexion() === 'twoFactor') {
      if (!this.code2faValide()) {
        this.error.set(true);
        this.errorMessage.set('Saisissez le code à 6 chiffres affiché dans Google Authenticator.');
        return;
      }
      this.verifierCode2fa();
      return;
    }
    if (!this.exigerTypeCompte()) {
      return;
    }
    if (this.modeTelephone() && this.etapeMembre() === 'telephone') {
      this.continuerMembre();
      return;
    }
    if (this.modeTelephone() && this.etapeMembre() === 'choix') {
      this.confirmerChoixCompte();
      return;
    }
    if (!this.validerAvantConnexion()) {
      return;
    }
    if (!this.validerFormulaireConnexion()) {
      return;
    }
    this.executerConnexion();
  }

  onCode2faInput(ev: Event): void {
    const raw = (ev.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 6);
    this.code2fa.set(raw);
    (ev.target as HTMLInputElement).value = raw;
  }

  retourIdentifiants(): void {
    this.etapeConnexion.set('identifiants');
    this.twoFactorToken.set('');
    this.code2fa.set('');
    this.error.set(false);
    this.errorMessage.set('');
  }

  private continuerMembre(): void {
    if (!this.validerIdentifiantSeul()) {
      return;
    }
    const tel = this.form.controls.identifiant.value.trim();
    this.lookupLoading.set(true);
    this.error.set(false);
    this.errorMessage.set('');
    this.auth.listerComptesMembre(tel).subscribe({
      next: (comptes) => {
        this.lookupLoading.set(false);
        if (!comptes.length) {
          this.error.set(true);
          this.errorMessage.set('Aucun compte membre actif pour ce numéro.');
          return;
        }
        this.comptesMembre.set(comptes);
        if (comptes.length === 1) {
          this.selectionnerCompte(comptes[0]);
          this.etapeMembre.set('motdepasse');
          this.synchroniserValidateurMotDePasse();
        } else {
          this.compteSelectionne.set(null);
          this.etapeMembre.set('choix');
          this.synchroniserValidateurMotDePasse();
        }
      },
      error: (err) => {
        this.lookupLoading.set(false);
        this.error.set(true);
        this.errorMessage.set(err?.error?.message ?? 'Impossible de vérifier ce numéro.');
      },
    });
  }

  private executerConnexion(): void {
    this.loading.set(true);
    this.error.set(false);
    this.errorMessage.set('');
    const { identifiant, motDePasse } = this.form.getRawValue();
    const preset = this.modeTelephone() ? 'membreSimple' : this.selectedRole()!;
    const roleSouhaite: Role | undefined =
      preset === 'super'
        ? 'SUPERADMIN'
        : preset === 'adminGie'
          ? 'ADMIN_GIE'
          : preset === 'membreBureau'
            ? 'MEMBRE'
            : undefined;
    const compte = estMembreSimple(preset) ? this.compteSelectionne() : null;

    this.auth
      .login(identifiant.trim(), motDePasse.trim(), {
        roleSouhaite,
        organisationId: compte?.organisationId,
        membreId: compte?.membreId,
      })
      .subscribe({
        next: (res) => {
          this.loading.set(false);
          if (res.requiresTwoFactor && res.twoFactorToken) {
            this.twoFactorToken.set(res.twoFactorToken);
            this.etapeConnexion.set('twoFactor');
            this.code2fa.set('');
            return;
          }
          this.redirigerApresConnexion(res);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(true);
          this.errorMessage.set(err?.error?.message ?? 'Identifiants incorrects.');
        },
      });
  }

  private verifierCode2fa(): void {
    if (!this.code2faValide()) {
      this.error.set(true);
      this.errorMessage.set('Saisissez le code à 6 chiffres affiché dans Google Authenticator.');
      return;
    }
    this.loading.set(true);
    this.error.set(false);
    this.auth.verifyTwoFactor(this.twoFactorToken(), this.code2fa()).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.redirigerApresConnexion(res);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(true);
        this.errorMessage.set(err?.error?.message ?? 'Code incorrect.');
      },
    });
  }

  private redirigerApresConnexion(res: Parameters<AuthService['redirectAfterLogin']>[0]): void {
    this.auth.redirectAfterLogin(res);
  }

  private reinitialiserFluxMembre(): void {
    this.etapeMembre.set('telephone');
    this.comptesMembre.set([]);
    this.compteSelectionne.set(null);
    this.etapeConnexion.set('identifiants');
    this.twoFactorToken.set('');
    this.code2fa.set('');
  }

  private appliquerModeDepuisSaisie(raw: string): void {
    const roleEmail = estConnexionEmail(this.selectedRole());
    const mode = resoutModeIdentifiant(raw, this.modeIdentifiant(), roleEmail);
    const prev = this.modeIdentifiant();

    if (mode === 'telephone' && prev !== 'telephone') {
      this.modeIdentifiant.set('telephone');
      this.roleRequis.set(false);
      this.error.set(false);
      this.errorMessage.set('');
      this.form.controls.motDePasse.setValue('');
      this.selectedGroupe.set('membre');
      this.selectedRole.set('membreSimple');
      this.reinitialiserFluxMembre();
      this.appliquerValidateursIdentifiant('telephone');
      this.synchroniserValidateurMotDePasse();
      return;
    }

    if (mode === 'email' && prev !== 'email') {
      this.forcerModeEmail();
      return;
    }

    if (mode === 'vide' && prev !== 'vide') {
      this.modeIdentifiant.set('vide');
      this.roleRequis.set(false);
      this.error.set(false);
      this.errorMessage.set('');
      this.form.controls.motDePasse.setValue('');
      this.selectedRole.set(null);
      this.selectedGroupe.set(null);
      this.reinitialiserFluxMembre();
      this.appliquerValidateursIdentifiant('vide');
      this.synchroniserValidateurMotDePasse();
    }
  }

  private forcerModeEmail(): void {
    this.modeIdentifiant.set('email');
    this.roleRequis.set(false);
    this.error.set(false);
    this.errorMessage.set('');
    if (estMembreSimple(this.selectedRole())) {
      this.selectedRole.set(null);
      this.selectedGroupe.set(null);
    }
    this.reinitialiserFluxMembre();
    this.appliquerValidateursIdentifiant('email');
    this.synchroniserValidateurMotDePasse();
  }

  private validerAvantConnexion(): boolean {
    if (this.afficherMotDePasse() && !this.form.controls.motDePasse.value.trim()) {
      this.error.set(true);
      this.errorMessage.set('Le mot de passe est obligatoire.');
      this.form.controls.motDePasse.markAsTouched();
      return false;
    }
    if (this.modeTelephone() && this.etapeMembre() === 'choix' && this.compteSelectionne() == null) {
      this.error.set(true);
      this.errorMessage.set('Sélectionnez le compte membre à ouvrir.');
      return false;
    }
    return true;
  }

  private synchroniserValidateurMotDePasse(): void {
    const ctrl = this.form.controls.motDePasse;
    const requis = this.afficherMotDePasse();
    if (requis) {
      ctrl.setValidators([Validators.required]);
    } else {
      ctrl.clearValidators();
      ctrl.setValue('', { emitEvent: false });
    }
    ctrl.updateValueAndValidity({ emitEvent: false });
  }

  private exigerTypeCompte(): boolean {
    if (this.modeTelephone()) {
      return true;
    }
    const identifiant = (this.form.controls.identifiant.value ?? '').trim();
    if (estSaisieEmailProbable(identifiant) && !this.modeEmail()) {
      this.forcerModeEmail();
    }
    if (this.modeEmail() && this.selectedRole() != null && !estMembreSimple(this.selectedRole())) {
      return true;
    }
    if (!identifiant) {
      this.error.set(true);
      this.errorMessage.set('Saisissez votre numéro de téléphone ou votre adresse email.');
      return false;
    }
    if (estSaisieEmailProbable(identifiant)) {
      this.roleRequis.set(true);
      this.error.set(true);
      this.errorMessage.set('Choisissez Membre de bureau, Admin GIE ou Superadmin.');
      return false;
    }
    this.error.set(true);
    this.errorMessage.set('Saisissez un numéro de téléphone ou une adresse email valide.');
    return false;
  }

  private appliquerValidateursIdentifiant(mode: 'telephone' | 'email' | 'vide'): void {
    const ctrl = this.form.controls.identifiant;
    if (mode === 'telephone') {
      ctrl.setValidators(validateursTelephoneLogin);
    } else if (mode === 'email') {
      ctrl.setValidators(validateursEmailLogin);
    } else {
      ctrl.setValidators([Validators.required]);
    }
    ctrl.updateValueAndValidity({ emitEvent: false });
  }

  private rolePourErreurIdentifiant(): LoginPreset | null {
    if (this.modeTelephone()) return 'membreSimple';
    return this.selectedRole();
  }

  private validerIdentifiantSeul(): boolean {
    const ctrl = this.form.controls.identifiant;
    ctrl.markAsTouched();
    ctrl.updateValueAndValidity();
    if (ctrl.valid) {
      return true;
    }
    this.error.set(true);
    this.errorMessage.set(
      messageErreurIdentifiant(ctrl.errors, this.rolePourErreurIdentifiant()) ??
        'Identifiant invalide.'
    );
    return false;
  }

  private validerFormulaireConnexion(): boolean {
    this.synchroniserValidateurMotDePasse();
    this.form.controls.identifiant.markAsTouched();
    if (this.afficherMotDePasse()) {
      this.form.controls.motDePasse.markAsTouched();
    }
    if (
      this.modeTelephone() &&
      this.etapeMembre() === 'motdepasse' &&
      this.plusieursComptes() &&
      this.compteSelectionne() == null
    ) {
      this.error.set(true);
      this.errorMessage.set('Sélectionnez le compte membre à ouvrir.');
      return false;
    }
    this.form.updateValueAndValidity();
    if (this.form.valid) {
      return true;
    }
    const msgId = messageErreurIdentifiant(
      this.form.controls.identifiant.errors,
      this.rolePourErreurIdentifiant()
    );
    if (msgId) {
      this.error.set(true);
      this.errorMessage.set(msgId);
      return false;
    }
    if (this.form.controls.motDePasse.errors?.['required']) {
      this.error.set(true);
      this.errorMessage.set('Le mot de passe est obligatoire.');
      return false;
    }
    return false;
  }
}
