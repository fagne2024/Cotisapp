import { Component, computed, effect, inject, OnInit, untracked } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/services/auth.service';
import { AppNotificationsService } from '../../core/services/app-notifications.service';
import { DroitAccesService } from '../../core/services/droit-acces.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import { MenuModuleId } from '../../core/util/modules-menu.util';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly notifs = inject(AppNotificationsService);
  private readonly droits = inject(DroitAccesService);

  readonly cotisationLinkActiveOpts = {
    paths: 'exact' as const,
    queryParams: 'exact' as const,
    matrixParams: 'ignored' as const,
    fragment: 'ignored' as const,
  };

  readonly remboursementLinkActiveOpts = {
    paths: 'subset' as const,
    queryParams: 'ignored' as const,
    matrixParams: 'ignored' as const,
    fragment: 'ignored' as const,
  };

  readonly penaliteLinkActiveOpts = {
    paths: 'subset' as const,
    queryParams: 'exact' as const,
    matrixParams: 'ignored' as const,
    fragment: 'ignored' as const,
  };

  readonly amendeLinkActiveOpts = this.penaliteLinkActiveOpts;

  orgShell(): number | null {
    return organisationCouranteId(this.route, this.auth);
  }

  readonly initials = computed(() => {
    const name = this.auth.nomComplet().trim();
    if (!name) return '?';
    return name
      .split(' ')
      .filter((p) => p.length > 0)
      .map((p) => p[0])
      .join('')
      .slice(0, 2)
      .toUpperCase();
  });

  readonly roleLabel = computed(() => {
    const r = this.auth.currentRole();
    if (r === 'ADMIN_GIE') return 'Admin GIE';
    if (r === 'SUPERADMIN') return 'Superadmin';
    return 'Membre';
  });

  /** Espace personnel (membre simple, pas de gestion GIE). */
  readonly estMembreSimple = computed(
    () => this.auth.currentRole() === 'MEMBRE' && !this.auth.compteBureau()
  );

  /** Compte bureau : menu filtré selon droits / modules du profil. */
  readonly estMembreBureau = computed(() => this.auth.compteBureau());

  /** Admin GIE ou superadmin : menu complet. */
  readonly estAdminComplet = computed(() =>
    this.auth.hasRole(['SUPERADMIN', 'ADMIN_GIE'])
  );

  readonly notifUnreadCount = this.notifs.unreadCount;

  readonly notificationsLink = computed((): (string | number)[] => {
    const org = this.orgShell();
    return org != null ? ['/organisations', org, 'notifications'] : [];
  });

  /** Lien d’accueil : tableau de bord pour tous les profils. */
  readonly accueilLink = computed((): (string | number)[] => {
    const org = this.orgShell();
    if (org == null) return [];
    return ['/organisations', org, 'dashboard'];
  });

  /** Visibilité d’un module menu (strictement selon /mes-droits → modules). */
  peutMenu(moduleId: MenuModuleId): boolean {
    if (this.estAdminComplet()) {
      return true;
    }
    if (!this.estMembreBureau()) {
      return false;
    }
    this.droits.droits();
    return this.droits.peutModule(moduleId);
  }

  constructor() {
    effect(() => {
      const orgId = this.orgShell();
      if (this.auth.compteBureau() && orgId != null && !this.droits.droits()) {
        untracked(() => {
          this.droits.chargerEtMemoriser(orgId).subscribe({
            next: (d) => this.droits.setDroits(d),
          });
        });
      }
    });

    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe(() => {
        this.rafraichirNotifications();
        this.rafraichirDroitsSiBureau();
      });
  }

  ngOnInit(): void {
    this.rafraichirNotifications();
    this.rafraichirDroitsSiBureau();
  }

  private rafraichirDroitsSiBureau(): void {
    const orgId = this.orgShell();
    if (!this.auth.compteBureau() || orgId == null) {
      return;
    }
    this.droits.chargerEtMemoriser(orgId).subscribe({
      next: (d) => this.droits.setDroits(d),
    });
  }

  private rafraichirNotifications(): void {
    const orgId = this.orgShell();
    if (orgId != null) {
      this.notifs.charger(orgId).subscribe();
    }
  }

  isParametrageRoute(): boolean {
    return this.router.url.includes('/parametrage/');
  }

  isTresorerieRoute(): boolean {
    return (
      this.router.url.includes('/gestion/tresorerie') || this.router.url.includes('/operations/depense-banque')
    );
  }

  logout(): void {
    this.auth.logout();
  }

  pageTitle(): string {
    const url = this.router.url;
    if (url.includes('dashboard')) return 'Tableau de bord';
    if (url.includes('mon-compte')) return 'Mon compte';
    if (url.includes('/membres/') && /\/membres\/\d+/.test(url)) return 'Fiche membre';
    if (url.includes('membres')) return 'Membres';
    if (url.includes('gestion/comptes')) return 'Comptes & Relevés';
    if (url.includes('cotisation-mois')) {
      if (url.includes('t=mois')) return 'Cotisation mensuelle (Mois)';
      return 'Cotisation hebdomadaire';
    }
    if (url.includes('operations/emprunts/suivi')) {
      return this.estMembreSimple() ? 'Mes emprunts' : 'Suivi des emprunts';
    }
    if (url.includes('operations/emprunts')) return 'Emprunts';
    if (url.includes('operations/remboursements') || url.includes('/remboursements/')) {
      if (url.includes('solidarite') || url.includes('t=solidarite')) return 'Remboursement — Solidarité';
      if (url.includes('caisse') || url.includes('t=caisse')) return 'Remboursement — Caisse';
      return 'Remboursement — Étalé';
    }
    if (url.includes('penalite-amende')) {
      if (url.includes('t=am')) return 'Amende';
      if (url.includes('t=pen')) return 'Pénalité';
      return 'Pénalité & Amende';
    }
    if (url.includes('gestion/tresorerie') || url.includes('depense-banque')) {
      return 'Trésorerie';
    }
    if (url.includes('/rapports')) return 'Rapports';
    if (url.includes('parametrage/')) return 'Paramétrage';
    if (url.includes('gestion/exercices')) return 'Exercices & PLANAD';
    if (url.includes('gestion/utilisateurs')) return "Utilisateurs & Droits d'accès";
    if (url.includes('notifications')) return 'Notifications';
    if (url.includes('mon-profil')) return 'Mon profil';
    if (url.includes('suivi-mensuel')) return 'Suivi mensuel';
    return 'CotisApp';
  }
}