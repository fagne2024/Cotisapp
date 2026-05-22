import { Injectable, computed, inject } from '@angular/core';
import { AuthService } from './auth.service';

export interface NavItem {
  label: string;
  route: string;
  icon: string;
  children?: NavItem[];
}

@Injectable({ providedIn: 'root' })
export class NavService {
  private readonly auth = inject(AuthService);

  readonly items = computed(() => {
    const role = this.auth.currentRole();
    const orgId = this.auth.currentOrgId();
    if (role === 'SUPERADMIN') {
      return [
        { label: 'Organisations', route: '/superadmin', icon: 'ti-building' },
      ] as NavItem[];
    }
    if (role === 'ADMIN_GIE' && orgId) {
      const base = `/organisations/${orgId}`;
      return [
        { label: 'Tableau de bord', route: `${base}/dashboard`, icon: 'ti-layout-dashboard' },
        { label: 'Membres', route: `${base}/membres`, icon: 'ti-users' },
        {
          label: 'Cotisation',
          route: `${base}/operations/cotisation-mois`,
          icon: 'ti-coin',
          children: [
            { label: 'Versement', route: `${base}/operations/cotisation-mois`, icon: 'ti-cash' },
            { label: 'Mois (nouveau)', route: `${base}/operations/cotisation-mois`, icon: 'ti-calendar' },
            { label: 'Suivi mensuel', route: `${base}/suivi-mensuel`, icon: 'ti-calendar-stats' },
          ],
        },
        {
          label: 'Remboursement',
          route: `${base}/operations/remboursements`,
          icon: 'ti-receipt-refund',
        },
        { label: 'Trésorerie', route: `${base}/gestion/tresorerie`, icon: 'ti-cash' },
        { label: 'Accès & Rôles', route: `${base}/gestion/utilisateurs`, icon: 'ti-lock' },
        { label: 'Paramétrage', route: `${base}/parametrage/regles`, icon: 'ti-settings' },
      ] as NavItem[];
    }
    if (role === 'MEMBRE' && orgId) {
      const base = `/organisations/${orgId}`;
      return [
        { label: 'Mon compte', route: `${base}/mon-compte`, icon: 'ti-user' },
        { label: 'Mes emprunts', route: `${base}/operations/emprunts/suivi`, icon: 'ti-cash-banknote' },
        { label: 'Mon rapport', route: `${base}/mon-compte/rapport`, icon: 'ti-report-analytics' },
      ] as NavItem[];
    }
    return [] as NavItem[];
  });
}
