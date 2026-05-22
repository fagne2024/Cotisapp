import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { TwoFactorService, TwoFactorSetup } from '../../../core/services/two-factor.service';

@Component({
  selector: 'app-configurer-2fa',
  standalone: true,
  templateUrl: './configurer-2fa.component.html',
  styleUrls: [
    '../changer-mot-de-passe-initial/changer-mot-de-passe-initial.component.scss',
    './configurer-2fa.component.scss',
  ],
})
export class Configurer2faComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly twoFactorApi = inject(TwoFactorService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly setup = signal<TwoFactorSetup | null>(null);
  readonly code = signal('');

  ngOnInit(): void {
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/login']);
      return;
    }
    if (this.auth.mustChangePassword()) {
      this.router.navigate(['/changer-mot-de-passe']);
      return;
    }
    if (!this.auth.mustSetupTwoFactor()) {
      const role = this.auth.currentRole();
      const orgId = this.auth.currentOrgId();
      if (role === 'SUPERADMIN') {
        this.router.navigate(['/superadmin']);
      } else if (orgId) {
        this.router.navigate(['/organisations', orgId, 'dashboard']);
      }
      return;
    }
    this.twoFactorApi.setup().subscribe({
      next: (s) => {
        this.setup.set(s);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Impossible de préparer la configuration 2FA.');
      },
    });
  }

  onCodeInput(ev: Event): void {
    const raw = (ev.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 6);
    this.code.set(raw);
    (ev.target as HTMLInputElement).value = raw;
  }

  confirmer(): void {
    if (!/^\d{6}$/.test(this.code())) {
      this.error.set('Saisissez le code à 6 chiffres affiché dans Google Authenticator.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.twoFactorApi.confirm(this.code()).subscribe({
      next: () => {
        this.saving.set(false);
        this.auth.terminerConfiguration2fa();
        const user = this.auth.user();
        if (user) {
          this.auth.redirectAfterLogin({
            userId: user.userId,
            email: user.email,
            nomComplet: user.nomComplet,
            role: user.role,
            organisationId: user.organisationId,
            organisationNom: user.organisationNom,
            membreId: user.membreId,
            mustChangePassword: false,
            mustSetupTwoFactor: false,
          });
        }
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.message ?? 'Code incorrect.');
      },
    });
  }

  deconnecter(): void {
    this.auth.logout();
  }
}
