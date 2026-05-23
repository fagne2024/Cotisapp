import { Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/services/auth.service';
import { OrganisationService } from '../../core/services/organisation.service';
import { SuperadminContextService } from '../../core/services/superadmin-context.service';
import { OrganisationDto } from '../../core/services/organisation.service';
import { AppNotificationsService } from '../../core/services/app-notifications.service';

@Component({
  selector: 'app-superadmin-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './superadmin-shell.component.html',
  styleUrl: './superadmin-shell.component.scss',
})
export class SuperadminShellComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly orgApi = inject(OrganisationService);
  readonly saContext = inject(SuperadminContextService);
  readonly notifs = inject(AppNotificationsService);

  readonly organisations = signal<OrganisationDto[]>([]);

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

  readonly activeOrgId = computed(() => this.orgIdFromRoute() ?? this.saContext.selectedOrgId());

  readonly hasOrg = computed(() => this.orgIdFromRoute() != null);

  readonly notifUnreadCount = this.notifs.unreadCount;

  readonly orgLabel = computed(() => {
    const id = this.activeOrgId();
    if (id == null) {
      return 'Sélectionnez une organisation';
    }
    const code = this.saContext.selectedOrgCode();
    const nom = this.saContext.selectedOrgNom();
    if (code && nom) {
      return `${nom} (${code})`;
    }
    return nom ?? `Organisation #${id}`;
  });

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

  private readonly pageTitleSignal = signal('Vue Superadmin');

  readonly pageTitle = this.pageTitleSignal.asReadonly();

  constructor() {
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe(() => this.syncRouteOrg());
  }

  ngOnInit(): void {
    this.orgApi.lister().subscribe({
      next: (list) => {
        this.organisations.set(list);
        this.syncRouteOrg();
      },
      error: () => this.syncRouteOrg(),
    });
    this.syncRouteOrg();
  }

  orgLink(segments: string[]): (string | number)[] {
    const id = this.orgIdFromRoute();
    if (id == null) {
      return ['/superadmin'];
    }
    return ['/superadmin', 'org', id, ...segments];
  }

  onOrgSelect(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    if (!raw) {
      this.saContext.clearOrg();
      void this.router.navigate(['/superadmin']);
      return;
    }
    const id = Number(raw);
    const org = this.organisations().find((o) => o.id === id);
    if (org) {
      this.saContext.selectOrg({ id: org.id, nom: org.nom, code: org.code });
      void this.router.navigate(['/superadmin', 'org', org.id, 'dashboard']);
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
    this.saContext.clearOrg();
    this.auth.logout();
  }

  private orgIdFromRoute(): number | null {
    let r: ActivatedRoute | null = this.route;
    while (r) {
      const raw = r.snapshot.paramMap.get('orgId');
      if (raw) {
        const id = Number(raw);
        if (!Number.isNaN(id)) {
          return id;
        }
      }
      r = r.parent;
    }
    return null;
  }

  private syncRouteOrg(): void {
    this.pageTitleSignal.set(this.resolvePageTitle());
    const id = this.orgIdFromRoute();
    if (id != null) {
      const org = this.organisations().find((o) => o.id === id);
      this.saContext.syncFromRoute(id, org?.nom, org?.code);
      this.notifs.charger(id).subscribe();
    }
  }

  private resolvePageTitle(): string {
    const url = this.router.url;
    if (url === '/superadmin' || url.endsWith('/superadmin')) {
      return 'Vue Superadmin';
    }
    if (url.includes('/superadmin/mon-profil')) {
      return 'Mon profil';
    }
    if (url.includes('dashboard')) {
      return 'Tableau de bord';
    }
    if (url.includes('/membres/') && /\/membres\/\d+/.test(url)) {
      return 'Fiche membre';
    }
    if (url.includes('membres')) {
      return 'Membres';
    }
    if (url.includes('cotisation-mois')) {
      return url.includes('t=mois') ? 'Cotisation mensuelle' : 'Cotisation hebdomadaire';
    }
    if (url.includes('operations/emprunts')) {
      return 'Emprunts';
    }
    if (url.includes('remboursements')) {
      return 'Remboursement';
    }
    if (url.includes('penalite-amende')) {
      if (url.includes('t=am')) return 'Amende';
      if (url.includes('t=pen')) return 'Pénalité';
      return 'Pénalité & Amende';
    }
    if (url.includes('gestion/tresorerie')) {
      return 'Trésorerie';
    }
    if (url.includes('rapports')) {
      return 'Rapports';
    }
    if (url.includes('parametrage/')) {
      return 'Paramétrage';
    }
    if (url.includes('gestion/utilisateurs')) {
      return 'Utilisateurs & Droits';
    }
    if (url.includes('notifications')) {
      return 'Notifications';
    }
    if (url.includes('suivi-mensuel')) {
      return 'Suivi mensuel';
    }
    return 'CotisApp Superadmin';
  }
}
