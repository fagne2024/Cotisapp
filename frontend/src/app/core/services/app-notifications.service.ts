import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { NotificationItem } from '../../features/notifications/notifications-demo.util';
import { buildOrgRoute } from '../util/notifications-route.util';
import { tap } from 'rxjs/operators';

export interface NotificationDto {
  id: string;
  groupe: string;
  severite: 'urgence' | 'warning' | 'info' | 'success';
  lu: boolean;
  icone: string;
  iconeClass: 'ico-re' | 'ico-or' | 'ico-g' | 'ico-bl' | 'ico-pu';
  titre: string;
  description: string;
  temps: string;
  tag: string;
  tagClass: 'tag-re' | 'tag-or' | 'tag-g' | 'tag-bl' | 'tag-pu' | 'tag-muted';
  actionLabel?: string | null;
  actionSegments?: string[] | null;
  actionQueryParams?: Record<string, string> | null;
  typeFiltre: 'EMPRUNT' | 'COTISATION' | 'SYSTEME';
  demandeId?: number | null;
  workflowDemande?: boolean;
  demandeWorkflowActif?: boolean;
  demandeTypeDemande?: string | null;
  amendeApplicable?: boolean;
  montantAmendeMin?: number | null;
  montantAmendeMax?: number | null;
}

export interface ApprouverDemandeBody {
  montantAmende?: number | null;
}

@Injectable({ providedIn: 'root' })
export class AppNotificationsService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly items = signal<NotificationItem[]>([]);
  readonly loading = signal(false);
  readonly lastOrgId = signal<number | null>(null);

  readonly unreadCount = computed(() => this.items().filter((n) => !n.lu).length);
  readonly urgenceCount = computed(
    () => this.items().filter((n) => !n.lu && n.severite === 'urgence').length
  );

  charger(orgId: number) {
    this.loading.set(true);
    this.lastOrgId.set(orgId);
    return this.http
      .get<NotificationDto[]>(`${environment.apiUrl}/organisations/${orgId}/notifications`)
      .pipe(
        tap({
          next: (list) => {
            this.items.set(list.map((d) => this.toItem(d, orgId)));
            this.loading.set(false);
          },
          error: () => {
            this.items.set([]);
            this.loading.set(false);
          },
        })
      );
  }

  marquerLu(orgId: number, cle: string) {
    const encoded = encodeURIComponent(cle);
    return this.http
      .put<void>(`${environment.apiUrl}/organisations/${orgId}/notifications/${encoded}/lire`, null)
      .pipe(
        tap(() => {
          this.items.update((list) =>
            list.map((n) =>
              n.id === cle
                ? { ...n, lu: true, tagClass: 'tag-muted' as const, groupe: n.groupe.includes('Lues') ? n.groupe : n.groupe }
                : n
            )
          );
        })
      );
  }

  marquerNonLu(orgId: number, cle: string) {
    const encoded = encodeURIComponent(cle);
    return this.http
      .put<void>(`${environment.apiUrl}/organisations/${orgId}/notifications/${encoded}/non-lire`, null)
      .pipe(
        tap(() => {
          this.items.update((list) =>
            list.map((n) =>
              n.id === cle
                ? { ...n, lu: false, tagClass: 'tag-re' as const }
                : n
            )
          );
        })
      );
  }

  marquerToutLu(orgId: number) {
    return this.http
      .put<void>(`${environment.apiUrl}/organisations/${orgId}/notifications/lire-tout`, null)
      .pipe(tap(() => this.charger(orgId).subscribe()));
  }

  masquer(orgId: number, cle: string) {
    const encoded = encodeURIComponent(cle);
    return this.http
      .put<void>(`${environment.apiUrl}/organisations/${orgId}/notifications/${encoded}/masquer`, null)
      .pipe(tap(() => this.items.update((list) => list.filter((n) => n.id !== cle))));
  }

  /** Retire immédiatement la notification d'une demande approuvée ou rejetée. */
  retirerDemandeTraitee(demandeId: number): void {
    this.items.update((list) => list.filter((n) => n.demandeId !== demandeId));
  }

  approuverDemande(orgId: number, demandeId: number, body?: ApprouverDemandeBody) {
    const payload =
      body?.montantAmende != null && body.montantAmende > 0
        ? { montantAmende: body.montantAmende }
        : {};
    return this.http
      .post<{ message?: string }>(
        `${environment.apiUrl}/organisations/${orgId}/demandes-operations/${demandeId}/approuver`,
        payload
      )
      .pipe(
        tap({
          next: () => {
            this.retirerDemandeTraitee(demandeId);
            this.charger(orgId).subscribe();
          },
        })
      );
  }

  refuserDemande(orgId: number, demandeId: number, motif: string) {
    return this.http
      .post<{ message?: string }>(
        `${environment.apiUrl}/organisations/${orgId}/demandes-operations/${demandeId}/refuser`,
        { motif }
      )
      .pipe(
        tap({
          next: () => {
            this.retirerDemandeTraitee(demandeId);
            this.charger(orgId).subscribe();
          },
        })
      );
  }

  private toItem(d: NotificationDto, orgId: number): NotificationItem {
    const segments = d.actionSegments ?? [];
    const actionRoute =
      segments.length > 0 ? buildOrgRoute(this.router, orgId, segments) : undefined;
    return {
      id: d.id,
      groupe: d.groupe,
      severite: d.severite,
      lu: d.lu,
      icone: d.icone,
      iconeClass: d.iconeClass,
      titre: d.titre,
      description: d.description,
      temps: d.temps,
      tag: d.tag,
      tagClass: d.tagClass,
      actionLabel: d.actionLabel ?? undefined,
      actionRoute,
      actionQueryParams: d.actionQueryParams ?? undefined,
      typeFiltre: d.typeFiltre as NotificationItem['typeFiltre'],
      demandeId: d.demandeId ?? undefined,
      workflowDemande: !!d.workflowDemande,
      demandeWorkflowActif: !!d.demandeWorkflowActif,
      demandeTypeDemande: d.demandeTypeDemande ?? undefined,
      amendeApplicable: !!d.amendeApplicable,
      montantAmendeMin: d.montantAmendeMin ?? undefined,
      montantAmendeMax: d.montantAmendeMax ?? undefined,
    };
  }
}
