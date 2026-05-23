import { Component, computed, inject, OnInit, signal, HostListener } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ExerciceDto, ExerciceService } from '../../core/services/exercice.service';
import {
  ParametrageClotureService,
  PreviewClotureExerciceDto,
} from '../../core/services/parametrage-cloture.service';
import { formatFcfa } from '../../core/utils/currency.util';
import { JourneeReunionDto, RecapJourneeService } from '../../core/services/recap-journee.service';
import { ConfirmDialogService } from '../../core/services/confirm-dialog.service';
import { NotificationService } from '../../core/services/notification.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import { paginateSlice } from '../../shared/util/pagination.util';
import { ClotureRepartitionPreviewComponent } from '../cloture/cloture-repartition-preview.component';
import { libelleModeRepartition } from '../cloture/cloture-preview.util';
import { DROIT_ACTION_IMPORTS } from '../../shared/imports/droit-action.imports';

@Component({
  selector: 'app-gestion-exercices',
  standalone: true,
  imports: [RouterLink, ListPaginationComponent, ClotureRepartitionPreviewComponent, ...DROIT_ACTION_IMPORTS],
  templateUrl: './gestion-exercices.component.html',
  styleUrl: './gestion-exercices.component.scss',
})
export class GestionExercicesComponent implements OnInit {
  readonly Math = Math;
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly exerciceService = inject(ExerciceService);
  private readonly parametrageClotureService = inject(ParametrageClotureService);
  private readonly recapJourneeService = inject(RecapJourneeService);
  private readonly notify = inject(NotificationService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  readonly pageSizeExercices = 10;
  readonly exercices = signal<ExerciceDto[]>([]);
  readonly courant = signal<ExerciceDto | null>(null);
  readonly planads = signal<JourneeReunionDto[]>([]);
  readonly chargement = signal(true);
  readonly transitionEnCours = signal(false);
  readonly cloturePlanadId = signal<number | null>(null);
  readonly reouverturePlanadId = signal<number | null>(null);
  readonly reouvertureExerciceId = signal<number | null>(null);
  readonly reinitialiserComptes = signal(false);
  readonly effectuerRepartition = signal(false);
  readonly observationCloture = signal('');
  readonly previewRepartition = signal<PreviewClotureExerciceDto | null>(null);
  readonly previewLoading = signal(false);
  readonly selectionId = signal<number | null>(null);
  readonly pageExercices = signal(1);
  readonly formatFcfa = formatFcfa;

  readonly estSuperadmin = computed(() => this.auth.currentRole() === 'SUPERADMIN');
  readonly orgId = computed(() => organisationCouranteId(this.route, this.auth) ?? 0);

  readonly exercicesTries = computed(() =>
    [...this.exercices()].sort((a, b) => b.numero - a.numero)
  );

  readonly exercicesPaged = computed(() =>
    paginateSlice(this.exercicesTries(), this.pageExercices(), this.pageSizeExercices)
  );

  readonly exerciceAffiche = computed(() => {
    const id = this.selectionId();
    if (id != null) {
      return this.exercices().find((e) => e.id === id) ?? this.courant();
    }
    return this.courant();
  });

  readonly vueCourant = computed(() => this.exerciceAffiche()?.courant === true);

  ngOnInit(): void {
    this.charger();
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey) {
      if (event.key === 'r' || event.key === 'R') {
        event.preventDefault();
        this.charger();
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.pageExercices.set(1);
    }
  }

  charger(): void {
    const orgId = this.orgId();
    if (orgId < 1) {
      this.chargement.set(false);
      return;
    }
    this.chargement.set(true);
    this.exerciceService.courant(orgId).subscribe({
      next: (c) => {
        this.courant.set(c);
        if (this.selectionId() == null) {
          this.selectionId.set(c.id);
        }
        this.recapJourneeService.lister(orgId, c.id).subscribe({
          next: (list) => this.planads.set(list),
          error: () => this.planads.set([]),
        });
        this.exerciceService.lister(orgId).subscribe({
          next: (list) => {
            this.exercices.set(list);
            this.chargement.set(false);
          },
          error: () => this.chargement.set(false),
        });
      },
      error: () => this.chargement.set(false),
    });
  }

  selectionnerExercice(e: ExerciceDto): void {
    this.selectionId.set(e.id);
  }

  formatDate(iso: string | null | undefined): string {
    if (!iso) return '—';
    const parts = iso.split('-');
    if (parts.length !== 3) return iso;
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }

  libellePlanad(e: ExerciceDto): string {
    if (e.statut === 'CLOTURE' && e.planadFin != null && e.planadFin > 0) {
      return `PLANAD n°1 → n°${e.planadFin}`;
    }
    if (e.courant) {
      if (e.planadOuvertLibelle) {
        return `${e.planadOuvertLibelle} en cours`;
      }
      if (e.nbPlanads > 0) {
        return `${e.nbPlanads} PLANAD — tous clôturés`;
      }
      return 'PLANAD n°1 en cours…';
    }
    return '—';
  }

  peutCloturerExercice(): boolean {
    const c = this.courant();
    return c != null && c.tousPlanadsClotures && c.nbPlanadsOuverts === 0;
  }

  peutReouvrirExercice(e: ExerciceDto): boolean {
    const c = this.courant();
    return (
      this.estSuperadmin() &&
      e.statut === 'CLOTURE' &&
      !e.courant &&
      c != null &&
      e.numero === c.numero - 1
    );
  }

  messageBlocageExercice(): string | null {
    const c = this.courant();
    if (!c || this.peutCloturerExercice()) return null;
    if (c.planadOuvertLibelle) {
      return `Clôturez d'abord le ${c.planadOuvertLibelle} avant de passer à l'exercice suivant.`;
    }
    if (c.nbPlanadsOuverts > 0) {
      return 'Clôturez tous les PLANAD ouverts avant de clôturer l\'exercice.';
    }
    return null;
  }

  confirmerCloturePlanad(j: JourneeReunionDto): void {
    if (j.statut === 'CLOTURE') return;
    void this.confirmDialog
      .confirm({
        title: 'Clôturer le PLANAD',
        message: `Clôturer le ${j.libelle} ? Aucune nouvelle opération ne sera possible sur cette date.`,
        confirmLabel: 'Clôturer',
        variant: 'danger',
      })
      .then((ok) => {
        if (!ok) return;
        this.executerCloturePlanad(j);
      });
  }

  private executerCloturePlanad(j: JourneeReunionDto): void {
    this.cloturePlanadId.set(j.id);
    this.recapJourneeService.cloturer(this.orgId(), j.id).subscribe({
      next: () => {
        this.notify.success(`${j.libelle} clôturé`);
        this.cloturePlanadId.set(null);
        this.charger();
      },
      error: (err) => {
        this.notify.error(err?.error?.message ?? 'Impossible de clôturer ce PLANAD');
        this.cloturePlanadId.set(null);
      },
    });
  }

  confirmerReouverturePlanad(j: JourneeReunionDto): void {
    if (j.statut !== 'CLOTURE') return;
    void this.confirmDialog
      .confirm({
        title: 'Réouvrir le PLANAD',
        message: `Réouvrir le ${j.libelle} ? Les opérations sur cette date seront à nouveau autorisées (superadmin).`,
        confirmLabel: 'Réouvrir',
      })
      .then((ok) => {
        if (!ok) return;
        this.executerReouverturePlanad(j);
      });
  }

  private executerReouverturePlanad(j: JourneeReunionDto): void {
    this.reouverturePlanadId.set(j.id);
    this.recapJourneeService.reouvrir(this.orgId(), j.id).subscribe({
      next: () => {
        this.notify.success(`${j.libelle} réouvert`);
        this.reouverturePlanadId.set(null);
        this.charger();
      },
      error: (err) => {
        this.notify.error(err?.error?.message ?? 'Impossible de réouvrir ce PLANAD');
        this.reouverturePlanadId.set(null);
      },
    });
  }

  confirmerReouvertureExercice(e: ExerciceDto): void {
    const c = this.courant();
    if (!c || !this.peutReouvrirExercice(e)) return;
    void this.confirmDialog
      .confirm({
        title: "Réouvrir l'exercice",
        message:
          `Réouvrir l'exercice n°${e.numero} ? L'exercice n°${c.numero} en cours sera clôturé automatiquement ` +
          `(uniquement s'il ne contient aucune donnée).`,
        confirmLabel: 'Réouvrir',
      })
      .then((ok) => {
        if (!ok) return;
        this.executerReouvertureExercice(e);
      });
  }

  private executerReouvertureExercice(e: ExerciceDto): void {
    this.reouvertureExerciceId.set(e.id);
    this.exerciceService.reouvrir(this.orgId(), e.id).subscribe({
      next: () => {
        this.notify.success(`Exercice n°${e.numero} réouvert`);
        this.reouvertureExerciceId.set(null);
        this.charger();
      },
      error: (err) => {
        this.notify.error(err?.error?.message ?? 'Impossible de réouvrir cet exercice');
        this.reouvertureExerciceId.set(null);
      },
    });
  }

  onRepartitionToggle(checked: boolean): void {
    this.effectuerRepartition.set(checked);
    if (checked) {
      this.chargerPreviewRepartition();
    } else {
      this.previewRepartition.set(null);
    }
  }

  chargerPreviewRepartition(): void {
    const orgId = this.orgId();
    if (orgId < 1) return;
    this.previewLoading.set(true);
    this.parametrageClotureService.previewRepartition(orgId).subscribe({
      next: (p) => {
        this.previewRepartition.set(p);
        this.previewLoading.set(false);
      },
      error: (err) => {
        this.previewRepartition.set(null);
        this.previewLoading.set(false);
        this.notify.error(err?.error?.message ?? 'Impossible de calculer la répartition.');
      },
    });
  }

  confirmerTransition(): void {
    const c = this.courant();
    if (!c || !this.peutCloturerExercice()) return;
    let msg = this.reinitialiserComptes()
      ? `Clôturer l'exercice n°${c.numero} et ouvrir l'exercice n°${c.numero + 1} avec remise à zéro des soldes ?`
      : `Clôturer l'exercice n°${c.numero} et ouvrir l'exercice n°${c.numero + 1} ?`;
    if (this.effectuerRepartition()) {
      const p = this.previewRepartition();
      if (p) {
        const mode = libelleModeRepartition(p.modeRepartition).toLowerCase();
        const detail =
          p.modeRepartition === 'EQUITABLE'
            ? `${p.membres.length} membre(s)`
            : `${p.totalParts} parts`;
        msg += `\n\nRépartition (${mode}) : ${formatFcfa(p.netADistribuer)} — ${detail}.`;
      } else {
        msg += '\n\nLa répartition configurée sera exécutée avant la transition.';
      }
    }
    msg += `\nLes données de l'exercice ${c.numero} restent consultables.`;
    void this.confirmDialog
      .confirm({
        title: 'Clôturer et ouvrir le suivant',
        message: msg,
        confirmLabel: 'Confirmer',
        variant: 'danger',
      })
      .then((ok) => {
        if (!ok) return;
        this.executerTransition(c);
      });
  }

  private executerTransition(c: ExerciceDto): void {
    this.transitionEnCours.set(true);
    this.exerciceService
      .transition(this.orgId(), {
        reinitialiserComptes: this.reinitialiserComptes(),
        effectuerRepartition: this.effectuerRepartition(),
        observationCloture: this.observationCloture().trim() || undefined,
      })
      .subscribe({
        next: (nouveau) => {
          this.notify.success(`Exercice n°${nouveau.numero} ouvert — les PLANAD repartent au n°1.`);
          this.observationCloture.set('');
          this.reinitialiserComptes.set(false);
          this.effectuerRepartition.set(false);
          this.previewRepartition.set(null);
          this.selectionId.set(nouveau.id);
          this.charger();
          this.transitionEnCours.set(false);
        },
        error: (err) => {
          this.notify.error(err?.error?.message ?? 'Impossible de clôturer cet exercice');
          this.transitionEnCours.set(false);
        },
      });
  }
}
