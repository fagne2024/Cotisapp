import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { AuthResponse, AuthUser, CompteMembreLogin, Role } from '../models/role.model';
import { DroitAccesService } from './droit-acces.service';

const TOKEN_KEY = 'cotisapp_token';
const USER_KEY = 'cotisapp_user';
const MUST_CHANGE_KEY = 'cotisapp_must_change_pwd';
const MUST_SETUP_2FA_KEY = 'cotisapp_must_setup_2fa';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly droits = inject(DroitAccesService);

  private readonly _user = signal<AuthUser | null>(this.loadUser());
  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => !!this._user());
  readonly currentRole = computed(() => this._user()?.role ?? null);
  readonly currentOrgId = computed(() => this._user()?.organisationId ?? null);
  readonly currentOrgNom = computed(() => this._user()?.organisationNom ?? null);
  readonly nomComplet = computed(() => this._user()?.nomComplet ?? '');
  readonly currentMembreId = computed(() => this._user()?.membreId ?? null);
  readonly compteBureau = computed(() => this._user()?.compteBureau === true);
  private readonly _mustChangePassword = signal(this.loadMustChangePassword());
  readonly mustChangePassword = this._mustChangePassword.asReadonly();
  private readonly _mustSetupTwoFactor = signal(this.loadMustSetupTwoFactor());
  readonly mustSetupTwoFactor = this._mustSetupTwoFactor.asReadonly();

  verifyTwoFactor(twoFactorToken: string, code: string) {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/verify-2fa`, {
        twoFactorToken,
        code: code.replace(/\s/g, ''),
      })
      .pipe(tap((res) => this.persistSession(res)));
  }

  login(
    identifiant: string,
    motDePasse: string,
    options?: { organisationId?: number; membreId?: number; roleSouhaite?: Role }
  ) {
    const body: {
      identifiant: string;
      motDePasse: string;
      organisationId?: number;
      membreId?: number;
      roleSouhaite?: Role;
    } = {
      identifiant,
      motDePasse,
    };
    if (options?.organisationId != null) {
      body.organisationId = options.organisationId;
    }
    if (options?.membreId != null) {
      body.membreId = options.membreId;
    }
    if (options?.roleSouhaite != null) {
      body.roleSouhaite = options.roleSouhaite;
    }
    return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/login`, body).pipe(
      tap((res) => {
        if (!res.requiresTwoFactor) {
          this.persistSession(res);
        }
      })
    );
  }

  listerComptesMembre(telephone: string) {
    return this.http.post<CompteMembreLogin[]>(`${environment.apiUrl}/auth/comptes-membre`, {
      telephone: telephone.trim(),
    });
  }

  changerMotDePasseInitial(nouveauMotDePasse: string, confirmationMotDePasse: string) {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/changer-mot-de-passe-initial`, {
        nouveauMotDePasse,
        confirmationMotDePasse,
      })
      .pipe(tap((res) => this.persistSession(res)));
  }

  logout(): void {
    const token = this.getToken();
    if (token) {
      this.http.post(`${environment.apiUrl}/auth/logout`, {}).subscribe({
        next: () => this.viderSession(),
        error: () => this.viderSession(),
      });
      return;
    }
    this.viderSession();
  }

  /** Vide la session localement sans appel réseau (utilisé par l'intercepteur 401). */
  clearSession(): void {
    this.viderSession();
  }

  private viderSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(MUST_CHANGE_KEY);
    localStorage.removeItem(MUST_SETUP_2FA_KEY);
    this._user.set(null);
    this._mustChangePassword.set(false);
    this._mustSetupTwoFactor.set(false);
    this.droits.clear();
    this.router.navigate(['/login']);
  }

  terminerConfiguration2fa(): void {
    localStorage.setItem(MUST_SETUP_2FA_KEY, 'false');
    this._mustSetupTwoFactor.set(false);
    const current = this._user();
    if (current) {
      const updated = { ...current, mustSetupTwoFactor: false };
      localStorage.setItem(USER_KEY, JSON.stringify(updated));
      this._user.set(updated);
    }
  }

  redirectAfterLogin(res: AuthResponse): void {
    if (res.mustChangePassword) {
      this.router.navigate(['/changer-mot-de-passe']);
      return;
    }
    if (res.mustSetupTwoFactor) {
      this.router.navigate(['/configurer-2fa']);
      return;
    }
    if (res.role === 'SUPERADMIN') {
      this.router.navigate(['/superadmin']);
    } else if (res.organisationId) {
      if (res.role === 'MEMBRE' && res.compteBureau) {
        this.droits.chargerEtMemoriser(res.organisationId).subscribe({
          next: (d) => this.droits.setDroits(d),
          error: () => {},
        });
      }
      this.router.navigate(['/organisations', res.organisationId, 'dashboard']);
    } else {
      // Réponse incomplète du backend (organisationId absent pour un rôle non SUPERADMIN)
      // → on redirige vers /login avec un paramètre d'erreur pour informer l'utilisateur
      this.router.navigate(['/login'], { queryParams: { erreur: 'session' } });
    }
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  hasRole(roles: Role[]): boolean {
    const role = this.currentRole();
    return role != null && roles.includes(role);
  }

  mettreAJourSession(partial: Partial<AuthUser>): void {
    const current = this._user();
    if (!current) {
      return;
    }
    const updated = { ...current, ...partial };
    localStorage.setItem(USER_KEY, JSON.stringify(updated));
    this._user.set(updated);
  }

  private loadUser(): AuthUser | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      const u = JSON.parse(raw) as AuthUser;
      if (u.role === 'MEMBRE' && u.compteBureau === undefined) {
        // Valeur manquante dans une session ancienne : on la déduit et on la repersiste
        u.compteBureau = u.membreId == null;
        localStorage.setItem(USER_KEY, JSON.stringify(u));
      }
      return u;
    } catch {
      return null;
    }
  }

  private loadMustChangePassword(): boolean {
    return localStorage.getItem(MUST_CHANGE_KEY) === 'true';
  }

  private loadMustSetupTwoFactor(): boolean {
    return localStorage.getItem(MUST_SETUP_2FA_KEY) === 'true';
  }

  private persistSession(res: AuthResponse): void {
    if (!res.token) {
      return;
    }
    localStorage.setItem(TOKEN_KEY, res.token);
    const mustChange = !!res.mustChangePassword;
    const mustSetup2fa = !!res.mustSetupTwoFactor;
    localStorage.setItem(MUST_CHANGE_KEY, mustChange ? 'true' : 'false');
    localStorage.setItem(MUST_SETUP_2FA_KEY, mustSetup2fa ? 'true' : 'false');
    const user: AuthUser = {
      userId: res.userId,
      email: res.email,
      nomComplet: res.nomComplet,
      role: res.role,
      organisationId: res.organisationId,
      organisationNom: res.organisationNom,
      membreId: res.membreId,
      compteBureau: res.compteBureau === true,
      mustChangePassword: mustChange,
      mustSetupTwoFactor: mustSetup2fa,
    };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this._user.set(user);
    this._mustChangePassword.set(mustChange);
    this._mustSetupTwoFactor.set(mustSetup2fa);
  }
}
