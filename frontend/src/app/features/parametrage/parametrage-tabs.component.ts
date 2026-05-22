import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { buildOrgRoute } from '../../core/util/notifications-route.util';
import { organisationCouranteId } from '../../core/util/org-route.util';

@Component({
  selector: 'app-parametrage-tabs',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="param-tabs-bar" aria-label="Sections paramétrage">
      <a
        class="param-tab"
        routerLinkActive="on"
        [routerLink]="lien('regles')"
        [routerLinkActiveOptions]="{ exact: true }"
        >⚙ Règles</a
      >
      <a
        class="param-tab"
        routerLinkActive="on"
        [routerLink]="lien('comptes')"
        [routerLinkActiveOptions]="{ exact: true }"
        >💳 Comptes</a
      >
      <a
        class="param-tab"
        routerLinkActive="on"
        [routerLink]="lien('cloture')"
        [routerLinkActiveOptions]="{ exact: true }"
        >📊 Clôture exercice</a
      >
    </nav>
  `,
  styleUrl: './parametrage-tabs.component.scss',
})
export class ParametrageTabsComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  lien(section: 'regles' | 'comptes' | 'cloture'): (string | number)[] {
    const orgId = organisationCouranteId(this.route, this.auth) ?? 0;
    return buildOrgRoute(this.router, orgId, ['parametrage', section]);
  }
}
