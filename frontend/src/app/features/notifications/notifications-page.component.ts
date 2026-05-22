import { Component, computed, effect, inject, signal, HostListener, untracked } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { DroitAccesService } from '../../core/services/droit-acces.service';
import { AppNotificationsService } from '../../core/services/app-notifications.service';
import { NotificationService } from '../../core/services/notification.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import { buildOrgRoute } from '../../core/util/notifications-route.util';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import {
  paginateSlice,
  paginationTotalPages,
} from '../../shared/util/pagination.util';
import {
  NotifPreference,
  NotifTab,
  NotifTypeFiltre,
  NotificationItem,
  PREFERENCES_DEMO,
} from './notifications-demo.util';

interface RejetDemandeDialog {
  notification: NotificationItem;
}

interface ApprobationDemandeDialog {
  notification: NotificationItem;
  appliquerAmende: boolean;
  montantAmende: number | null;
}

@Component({
  selector: 'app-notifications-page',
  standalone: true,
  imports: [RouterLink, ConfirmDialogComponent, ListPaginationComponent],
  templateUrl: './notifications-page.component.html',
  styleUrls: ['./notifications-page.component.scss', '../../shared/styles/pagination.scss'],
})
export class NotificationsPageComponent {
  readonly pageSize = 10;
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly notifsApi = inject(AppNotificationsService);
  private readonly notify = inject(NotificationService);
  private readonly droits = inject(DroitAccesService);

  readonly estMembreSimple = computed(
    () => this.auth.currentRole() === 'MEMBRE' && this.auth.currentMembreId() != null
  );

  readonly peutValiderDemandes = computed(() => {
    if (this.auth.hasRole(['SUPERADMIN', 'ADMIN_GIE'])) {
      return true;
    }
    const orgId = this.orgId();
    return orgId != null && this.droits.peutGestion(orgId);
  });
  readonly workflowEnAttenteCount = computed(
    () =>
      this.notifications().filter((n) => n.demandeWorkflowActif && !n.lu && n.demandeId != null).length
  );
  readonly traitementDemandeId = signal<number | null>(null);
  readonly rejetDialog = signal<RejetDemandeDialog | null>(null);
  readonly approbationDialog = signal<ApprobationDemandeDialog | null>(null);

  readonly tab = signal<NotifTab>('toutes');
  readonly typeFiltre = signal<NotifTypeFiltre>('ALL');
  readonly page = signal(1);
  readonly preferences = signal<NotifPreference[]>(PREFERENCES_DEMO.map((p) => ({ ...p })));

  readonly orgId = computed(() => organisationCouranteId(this.route, this.auth));
  readonly notifications = this.notifsApi.items;
  readonly loading = this.notifsApi.loading;

  readonly unreadCount = computed(() => this.notifications().filter((n) => !n.lu).length);
  readonly urgenceCount = computed(
    () => this.notifications().filter((n) => !n.lu && n.severite === 'urgence').length
  );
  readonly luesCount = computed(() => this.notifications().filter((n) => n.lu).length);
  readonly totalCount = computed(() => this.notifications().length);

  readonly filteredTotal = computed(() => this.filteredNotifications().length);

  readonly paginatedNotifications = computed(() =>
    paginateSlice(this.filteredNotifications(), this.page(), this.pageSize)
  );

  readonly groupes = computed(() => {
    const list = this.paginatedNotifications();
    const order: string[] = [];
    const map = new Map<string, NotificationItem[]>();
    for (const n of list) {
      if (!map.has(n.groupe)) {
        map.set(n.groupe, []);
        order.push(n.groupe);
      }
      map.get(n.groupe)!.push(n);
    }
    return order.map((label) => ({ label, items: map.get(label)! }));
  });

  readonly filteredNotifications = computed(() => {
    let list = this.notifications();
    const tab = this.tab();
    if (tab === 'nonlues') {
      list = list.filter((n) => !n.lu);
    } else if (tab === 'lues') {
      list = list.filter((n) => n.lu);
    } else if (tab === 'urgences') {
      list = list.filter((n) => n.severite === 'urgence');
    }
    const tf = this.typeFiltre();
    if (tf === 'URGENCE') {
      list = list.filter((n) => n.severite === 'urgence');
    } else if (tf === 'EMPRUNT') {
      list = list.filter((n) => n.typeFiltre === 'EMPRUNT');
    } else if (tf === 'COTISATION') {
      list = list.filter((n) => n.typeFiltre === 'COTISATION');
    } else if (tf === 'SYSTEME') {
      list = list.filter((n) => n.typeFiltre === 'SYSTEME');
    } else if (tf === 'A_VALIDER') {
      list = list.filter((n) => n.demandeWorkflowActif && n.demandeId != null);
    }
    return list;
  });

  readonly stats = computed(() => ({
    urgences: this.notifications().filter((n) => !n.lu && n.severite === 'urgence').length,
    warnings: this.notifications().filter((n) => !n.lu && n.severite === 'warning').length,
    infos: this.notifications().filter((n) => !n.lu && (n.severite === 'info' || n.severite === 'success')).length,
    lues: this.luesCount(),
  }));

  constructor() {
    effect(() => {
      const orgId = this.orgId();
      if (orgId != null) {
        untracked(() => this.notifsApi.charger(orgId).subscribe());
      }
    });
    effect(() => {
      this.filteredNotifications();
      this.tab();
      this.typeFiltre();
      const tp = paginationTotalPages(this.filteredTotal(), this.pageSize);
      const p = this.page();
      if (p > tp) {
        untracked(() => this.page.set(Math.max(1, tp)));
      }
    });
  }

  setTab(tab: NotifTab): void {
    this.tab.set(tab);
    this.page.set(1);
  }

  voirDemandesAValider(): void {
    this.tab.set('urgences');
    this.typeFiltre.set('A_VALIDER');
    this.page.set(1);
  }

  onTypeFiltreChange(event: Event): void {
    const v = (event.target as HTMLSelectElement).value as NotifTypeFiltre;
    this.typeFiltre.set(v);
    this.page.set(1);
  }

  dismiss(id: string, event: Event): void {
    event.stopPropagation();
    const orgId = this.orgId();
    if (orgId == null) {
      return;
    }
    this.notifsApi.masquer(orgId, id).subscribe();
  }

  marquerToutLu(): void {
    const orgId = this.orgId();
    if (orgId == null) {
      return;
    }
    this.notifsApi.marquerToutLu(orgId).subscribe();
  }

  marquerLu(n: NotificationItem): void {
    if (n.lu) {
      return;
    }
    const orgId = this.orgId();
    if (orgId == null) {
      return;
    }
    this.notifsApi.marquerLu(orgId, n.id).subscribe();
  }

  marquerNonLu(n: NotificationItem, event: Event): void {
    event.stopPropagation();
    if (!n.lu) {
      return;
    }
    const orgId = this.orgId();
    if (orgId == null) {
      return;
    }
    this.notifsApi.marquerNonLu(orgId, n.id).subscribe();
  }

  togglePreference(id: string): void {
    this.preferences.update((prefs) =>
      prefs.map((p) => (p.id === id ? { ...p, actif: !p.actif } : p))
    );
  }

  orgBase(): (string | number)[] {
    const id = this.orgId();
    return id != null ? buildOrgRoute(this.router, id, []) : [];
  }

  ouvrirApprobationDemande(n: NotificationItem, event: Event): void {
    event.stopPropagation();
    if (n.demandeId == null || this.traitementDemandeId() != null) {
      return;
    }
    const cotisation =
      n.demandeTypeDemande === 'COTISATION_HEBDO' || n.demandeTypeDemande === 'COTISATION_MOIS';
    if (!cotisation || !n.amendeApplicable) {
      this.executerApprobation(n, undefined);
      return;
    }
    const min = n.montantAmendeMin ?? 500;
    this.approbationDialog.set({
      notification: n,
      appliquerAmende: false,
      montantAmende: min,
    });
  }

  fermerApprobationDemande(): void {
    this.approbationDialog.set(null);
  }

  toggleAmendeApprobation(): void {
    this.approbationDialog.update((d) =>
      d != null ? { ...d, appliquerAmende: !d.appliquerAmende } : d
    );
  }

  onMontantAmendeApprobationInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    const parsed = raw === '' ? null : Number(raw);
    const montant = parsed != null && !Number.isNaN(parsed) ? parsed : null;
    this.approbationDialog.update((d) =>
      d != null ? { ...d, montantAmende: montant } : d
    );
  }

  confirmerApprobationDemande(): void {
    const dlg = this.approbationDialog();
    if (dlg == null) {
      return;
    }
    let montant: number | undefined;
    if (dlg.appliquerAmende) {
      const m = dlg.montantAmende;
      const min = dlg.notification.montantAmendeMin ?? 0;
      const max = dlg.notification.montantAmendeMax ?? Number.MAX_SAFE_INTEGER;
      if (m == null || m <= 0) {
        this.notify.show('Indiquez le montant de l\'amende.');
        return;
      }
      if (m < min || m > max) {
        this.notify.show(`Montant amende invalide (${this.formatFcfa(min)} – ${this.formatFcfa(max)}).`);
        return;
      }
      montant = m;
    }
    this.fermerApprobationDemande();
    this.executerApprobation(dlg.notification, montant);
  }

  private executerApprobation(n: NotificationItem, montantAmende: number | undefined): void {
    const orgId = this.orgId();
    const demandeId = n.demandeId;
    if (orgId == null || demandeId == null || this.traitementDemandeId() != null) {
      return;
    }
    this.traitementDemandeId.set(demandeId);
    const body = montantAmende != null && montantAmende > 0 ? { montantAmende } : undefined;
    this.notifsApi.approuverDemande(orgId, demandeId, body).subscribe({
      next: (res) => {
        this.traitementDemandeId.set(null);
        const suffix =
          montantAmende != null && montantAmende > 0
            ? ` Amende : ${this.formatFcfa(montantAmende)}.`
            : '';
        this.notify.show((res?.message ?? 'Demande approuvée et comptabilisée.') + suffix);
      },
      error: (err) => {
        this.traitementDemandeId.set(null);
        const msg = err?.error?.message ?? 'Impossible d\'approuver la demande.';
        this.notify.show(typeof msg === 'string' ? msg : 'Erreur lors de l\'approbation.');
      },
    });
  }

  formatFcfa(n: number): string {
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(n) + ' FCFA';
  }

  ouvrirRejetDemande(n: NotificationItem, event: Event): void {
    event.stopPropagation();
    if (n.demandeId == null || this.traitementDemandeId() != null) {
      return;
    }
    this.rejetDialog.set({ notification: n });
  }

  fermerRejetDemande(): void {
    this.rejetDialog.set(null);
  }

  confirmerRejetDemande(motif: string): void {
    const dlg = this.rejetDialog();
    const orgId = this.orgId();
    const demandeId = dlg?.notification.demandeId;
    if (dlg == null || orgId == null || demandeId == null || this.traitementDemandeId() != null) {
      return;
    }
    this.traitementDemandeId.set(demandeId);
    this.notifsApi.refuserDemande(orgId, demandeId, motif).subscribe({
      next: (res) => {
        this.traitementDemandeId.set(null);
        this.fermerRejetDemande();
        this.notify.show(res?.message ?? 'Demande rejetée.');
      },
      error: (err) => {
        this.traitementDemandeId.set(null);
        const msg = err?.error?.message ?? 'Impossible de rejeter la demande.';
        this.notify.show(typeof msg === 'string' ? msg : 'Erreur lors du rejet.');
      },
    });
  }
}
