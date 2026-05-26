import { Component, OnInit, computed, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { MobileDataService } from '../shared/mobile-data.service';
import { organisationCouranteId } from '../../../core/util/org-route.util';

@Component({
  selector: 'app-mobile-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './mobile-shell.component.html',
  styleUrl: './mobile-shell.component.scss',
})
export class MobileShellComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly data = inject(MobileDataService);

  protected orgId = 0;

  protected readonly orgNom = computed(() => this.auth.currentOrgNom() ?? 'CotisApp');
  protected readonly prenom = computed(() => {
    const n = this.auth.nomComplet();
    return n ? n.split(' ')[0] : '';
  });

  ngOnInit(): void {
    const id = organisationCouranteId(this.route, this.auth);
    if (id == null) {
      this.router.navigate(['/login']);
      return;
    }
    this.orgId = id;
    this.data.charger(id);
  }

  protected get navBase(): string {
    return `/m/organisations/${this.orgId}`;
  }

  protected logout(): void {
    this.data.reset();
    this.auth.logout();
  }
}
