import { Component, computed, inject, signal, HostListener } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { CompteMembreLogin, Role } from '../../../core/models/role.model';
import {
  messageErreurIdentifiant,
  validateursEmailLogin,
  validateursTelephoneLogin,
} from './login-identifiant.validators';
import {
  estConnexionEmail,
  estMembreBureau,
  estMembreSimple,
  groupePourPreset,
  libelleLoginGroupe,
  libelleLoginPreset,
  libelleSousTypeAttendu,
  LoginGroupe,
  LoginPreset,
} from './login-preset.util';
type EtapeMembre = 'telephone' | 'choix' | 'motdepasse';
type EtapeConnexion = 'identifiants' | 'twoFactor';

const DEMO_SUPERADMIN = {
  identifiant: 'superadmin@cotisapp.sn',
  motDePasse: 'Admin@2026',
};

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  readonly Math = Math;
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly error = signal(false);
  readonly errorMessage = signal('');
  readonly loading = signal(false);
  readonly lookupLoading = signal(false);
  readonly showPassword = signal(false);
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
    identifiant: [{ value: '', disabled: true }, Validators.required],
    motDePasse: ['', Validators.required],
  });

  readonly groupeSelectionne = computed(() => this.selectedGroupe() != null);
  readonly roleSelectionne = computed(() => this.selectedRole() != null);

  readonly identifiantLabel = computed(() => {
    const role = this.selectedRole();
    if (estMembreSimple(role)) return 'Numéro de téléphone';
    if (role == null) return 'Identifiant';
    return 'Adresse email';
  });

  readonly identifiantPlaceholder = computed(() => {
    const role = this.selectedRole();
    if (role == null) return 'Choisissez d’abord un type de compte ci-dessus';
    if (role === 'super') return 'superadmin@cotisapp.sn';
    if (estMembreSimple(role)) return '+221 77 000 00 00';
    if (estMembreBureau(role)) return 'sg@mon-gie.sn';
    return 'votre.email@exemple.sn';
  });

  readonly identifiantInputType = computed(() =>
    estMembreSimple(this.selectedRole()) ? 'tel' : 'email'
  );

  readonly identifiantAutocomplete = computed(() =>
    estMembreSimple(this.selectedRole()) ? 'tel' : 'email'
  );

  readonly roleHint = computed(() => {
    if (!this.roleSelectionne()) {
      return 'Choisissez un type de compte ci-dessus avant de continuer.';
    }
    const role = this.selectedRole();
    if (role === 'super') {
      return 'Accès plateforme : gestion de toutes les organisations.';
    }
    if (estMembreSimple(role)) {
      return 'Numéro de téléphone obligatoire (format international accepté, ex. +221 77 …).';
    }
    if (estMembreBureau(role)) {
      return 'Email de votre compte bureau (SG, trésorier, superviseur…). Mot de passe initial : Passer123.';
    }
    return 'Adresse email obligatoire pour la connexion administrateur.';
  });

  readonly erreurIdentifiant = computed(() => {
    const ctrl = this.form.controls.identifiant;
    if (!ctrl.touched && !ctrl.dirty) return null;
    return messageErreurIdentifiant(ctrl.errors, this.selectedRole());
  });

  readonly afficherChampsIdentifiants = computed(
    () => this.roleSelectionne() && this.etapeConnexion() === 'identifiants'
  );

  readonly afficherMotDePasse = computed(
    () =>
      this.etapeConnexion() === 'identifiants' &&
      (estConnexionEmail(this.selectedRole()) || this.etapeMembre() === 'motdepasse')
  );

  readonly afficherEtape2fa = computed(() => this.etapeConnexion() === 'twoFactor');

  readonly plusieursComptes = computed(() => this.comptesMembre().length > 1);

  /** Liste à cocher : étape dédiée ou changement sur l’écran mot de passe. */
  readonly afficherListeComptes = computed(() => {
    if (!estMembreSimple(this.selectedRole()) || this.comptesMembre().length <= 1) {
      return false;
    }
    return this.etapeMembre() === 'choix' || this.etapeMembre() === 'motdepasse';
  });

  readonly afficherCompteUnique = computed(
    () =>
      estMembreSimple(this.selectedRole()) &&
      this.comptesMembre().length === 1 &&
      this.etapeMembre() === 'motdepasse' &&
      this.compteSelectionne() != null
  );

  readonly afficherBoutonContinuer = computed(
    () =>
      this.roleSelectionne() &&
      this.etapeConnexion() === 'identifiants' &&
      estMembreSimple(this.selectedRole()) &&
      this.etapeMembre() === 'telephone'
  );

  readonly afficherBoutonContinuerChoix = computed(
    () =>
      this.roleSelectionne() &&
      this.etapeConnexion() === 'identifiants' &&
      estMembreSimple(this.selectedRole()) &&
      this.etapeMembre() === 'choix'
  );

  readonly afficherBoutonConnexion = computed(
    () =>
      this.roleSelectionne() &&
      this.etapeConnexion() === 'identifiants' &&
      (estConnexionEmail(this.selectedRole()) || this.etapeMembre() === 'motdepasse')
  );

  readonly afficherBouton2fa = computed(() => this.etapeConnexion() === 'twoFactor');

  readonly libelleBoutonPrincipal = computed(() => {
    if (this.loading()) return 'Connexion en cours…';
    if (this.lookupLoading()) return 'Recherche…';
    if (this.afficherBouton2fa()) return 'Valider le code →';
    if (this.afficherBoutonContinuer()) return 'Continuer →';
    if (this.afficherBoutonContinuerChoix()) return 'Continuer →';
    return 'Se connecter →';
  });

  readonly boutonChoixDesactive = computed(
    () => this.afficherBoutonContinuerChoix() && this.compteSelectionne() == null
  );

  readonly code2faValide = computed(() => /^\d{6}$/.test(this.code2fa().replace(/\s/g, '')));

  setGroupe(groupe: LoginGroupe): void {
    if (this.selectedGroupe() === groupe) {
      return;
    }
    this.selectedGroupe.set(groupe);
    this.selectedRole.set(null);
    this.roleRequis.set(false);
    this.reinitialiserFluxMembre();
    this.form.controls.identifiant.disable({ emitEvent: false });
    this.form.patchValue({ identifiant: '', motDePasse: '' });
    this.error.set(false);
    this.errorMessage.set('');
  }

  setRole(role: LoginPreset): void {
    this.selectedGroupe.set(groupePourPreset(role));
    this.selectedRole.set(role);
    this.roleRequis.set(false);
    this.reinitialiserFluxMembre();
    this.form.controls.identifiant.enable({ emitEvent: false });
    this.appliquerValidateursIdentifiant(role);
    if (role === 'super') {
      this.form.patchValue({
        identifiant: DEMO_SUPERADMIN.identifiant,
        motDePasse: DEMO_SUPERADMIN.motDePasse,
      });
    } else {
      this.form.patchValue({ identifiant: '', motDePasse: '' });
    }
    this.form.controls.identifiant.markAsUntouched();
    this.error.set(false);
    this.errorMessage.set('');
  }

  isGroupeActive(groupe: LoginGroupe): boolean {
    return this.selectedGroupe() === groupe;
  }

  isRoleActive(role: LoginPreset): boolean {
    return this.selectedRole() === role;
  }

  libelleRoleSelectionne(): string {
    const role = this.selectedRole();
    if (role) {
      return libelleLoginPreset(role);
    }
    const g = this.selectedGroupe();
    if (g) {
      return `${libelleLoginGroupe(g)} — choisir : ${libelleSousTypeAttendu(g)}`;
    }
    return '';
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
  }

  retourChoixCompte(): void {
    if (this.plusieursComptes()) {
      this.etapeMembre.set('choix');
    } else {
      this.etapeMembre.set('telephone');
    }
    this.compteSelectionne.set(null);
    this.form.controls.motDePasse.setValue('');
  }

  retourTelephone(): void {
    this.etapeMembre.set('telephone');
    this.comptesMembre.set([]);
    this.compteSelectionne.set(null);
    this.form.controls.motDePasse.setValue('');
    this.error.set(false);
    this.errorMessage.set('');
  }

  submit(): void {
    if (this.etapeConnexion() === 'twoFactor') {
      this.verifierCode2fa();
      return;
    }
    if (!this.exigerTypeCompte()) {
      return;
    }
    if (estMembreSimple(this.selectedRole()) && this.etapeMembre() === 'choix') {
      this.confirmerChoixCompte();
      return;
    }
    if (estMembreSimple(this.selectedRole()) && this.etapeMembre() === 'telephone') {
      this.continuerMembre();
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
        } else {
          this.compteSelectionne.set(null);
          this.etapeMembre.set('choix');
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
    const preset = this.selectedRole()!;
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

  private exigerTypeCompte(): boolean {
    if (this.selectedRole() != null) {
      return true;
    }
    this.roleRequis.set(true);
    this.error.set(true);
    if (this.selectedGroupe() != null) {
      this.errorMessage.set(`Choisissez ${libelleSousTypeAttendu(this.selectedGroupe())}.`);
    } else {
      this.errorMessage.set('Choisissez ADMIN ou MEMBRE, puis le sous-type de compte.');
    }
    return false;
  }

  private appliquerValidateursIdentifiant(role: LoginPreset): void {
    const ctrl = this.form.controls.identifiant;
    if (estMembreSimple(role)) {
      ctrl.setValidators(validateursTelephoneLogin);
    } else {
      ctrl.setValidators(validateursEmailLogin);
    }
    ctrl.updateValueAndValidity({ emitEvent: false });
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
      messageErreurIdentifiant(ctrl.errors, this.selectedRole()) ??
        'Identifiant invalide.'
    );
    return false;
  }

  private validerFormulaireConnexion(): boolean {
    this.form.controls.identifiant.markAsTouched();
    if (this.afficherMotDePasse()) {
      this.form.controls.motDePasse.markAsTouched();
    }
    if (
      estMembreSimple(this.selectedRole()) &&
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
      this.selectedRole()
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
