import { Component, computed, inject, input, OnInit, signal, HostListener } from '@angular/core';
import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import { paginateSlice } from '../../shared/util/pagination.util';
import { MembreService, RecapMembreJourneeDto } from '../../core/services/membre.service';
import { JourneeReunionDto } from '../../core/services/recap-journee.service';
import { formatFcfa } from '../../core/utils/currency.util';
import {
  planadBadgeFromLibelle,
  statutMembreLabel,
  typeOperationMeta,
} from '../recap-journee/recap-journee-display.util';
import { filtrerJourneesParNumero, filtrePlanadNumeroActif } from '../recap-journee/recap-journee-filtre.util';

@Component({
  selector: 'app-membre-recap-journee',
  standalone: true,
  imports: [ListPaginationComponent],
  templateUrl: './membre-recap-journee.component.html',
  styleUrls: ['./membre-recap-journee.component.scss', '../recap-journee/recap-journee.component.scss'],
})
export class MembreRecapJourneeComponent implements OnInit {
  readonly orgId = input.required<number>();

  private readonly membreService = inject(MembreService);

  readonly formatFcfa = formatFcfa;
  readonly planadBadgeFromLibelle = planadBadgeFromLibelle;
  readonly statutMembreLabel = statutMembreLabel;
  readonly typeOperationMeta = typeOperationMeta;

  readonly journees = signal<JourneeReunionDto[]>([]);
  readonly recap = signal<RecapMembreJourneeDto | null>(null);
  readonly loadingListe = signal(true);
  readonly loadingRecap = signal(false);
  readonly dateSaisie = signal(this.todayIso());
  readonly journeeSelectionneeId = signal<number | null>(null);
  readonly pageJournees = signal(1);
  readonly pageOperations = signal(1);
  readonly filtrePlanadNumero = signal('');
  readonly filtreTypeOperation = signal('');
  readonly filtreStatutOperation = signal('');

  readonly pageSizeJournees = 8;
  readonly pageSizeOperations = 15;

  readonly filtrePlanadActif = computed(() => filtrePlanadNumeroActif(this.filtrePlanadNumero()));

  readonly journeesFiltrees = computed(() =>
    filtrerJourneesParNumero(this.journees(), this.filtrePlanadNumero())
  );

  readonly journeesPaged = computed(() =>
    paginateSlice(this.journeesFiltrees(), this.pageJournees(), this.pageSizeJournees)
  );

  readonly operationsFiltres = computed(() => {
    let list = this.recap()?.operations ?? [];
    const type = this.filtreTypeOperation();
    if (type) {
      list = list.filter((o) => o.typeOperation === type);
    }
    const statut = this.filtreStatutOperation();
    if (statut === 'ACTIVE') {
      list = list.filter((o) => !o.annulee && !o.annulation);
    } else if (statut === 'ANNULEE') {
      list = list.filter((o) => o.annulee);
    } else if (statut === 'ANNULATION') {
      list = list.filter((o) => o.annulation);
    }
    return list;
  });

  readonly operationsPaged = computed(() =>
    paginateSlice(this.operationsFiltres(), this.pageOperations(), this.pageSizeOperations)
  );

  ngOnInit(): void {
    this.chargerListe();
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey) {
      if (event.key === 'r' || event.key === 'R') {
        event.preventDefault();
        this.chargerListe();
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.filtrePlanadNumero.set('');
      this.filtreTypeOperation.set('');
    }
  }

  chargerListe(): void {
    this.loadingListe.set(true);
    this.membreService.listerRecapJourneesMonCompte(this.orgId()).subscribe({
      next: (list) => {
        this.journees.set(list);
        this.loadingListe.set(false);
        const sel = this.journeeSelectionneeId();
        if (sel != null && list.some((j) => j.id === sel)) {
          this.chargerRecap(sel);
        } else if (list.length) {
          this.selectionnerJournee(list[0].id);
        } else {
          this.recap.set(null);
        }
      },
      error: () => {
        this.journees.set([]);
        this.loadingListe.set(false);
      },
    });
  }

  selectionnerJournee(id: number): void {
    this.journeeSelectionneeId.set(id);
    this.pageOperations.set(1);
    this.chargerRecap(id);
  }

  chargerParDate(): void {
    const date = this.dateSaisie();
    if (!date) return;
    this.loadingRecap.set(true);
    this.membreService.obtenirRecapJourneeMonCompteParDate(this.orgId(), date).subscribe({
      next: (r) => {
        this.recap.set(r);
        this.journeeSelectionneeId.set(r.journeeId);
        this.loadingRecap.set(false);
        if (!this.journees().some((j) => j.id === r.journeeId)) {
          this.chargerListe();
        }
      },
      error: () => {
        this.recap.set(null);
        this.loadingRecap.set(false);
      },
    });
  }

  private chargerRecap(journeeId: number): void {
    this.loadingRecap.set(true);
    this.membreService.obtenirRecapJourneeMonCompte(this.orgId(), journeeId).subscribe({
      next: (r) => {
        this.recap.set(r);
        this.dateSaisie.set(r.dateReunion);
        this.loadingRecap.set(false);
      },
      error: () => {
        this.recap.set(null);
        this.loadingRecap.set(false);
      },
    });
  }

  onFiltrePlanadNumero(ev: Event): void {
    this.filtrePlanadNumero.set((ev.target as HTMLInputElement).value);
    this.pageJournees.set(1);
  }

  effacerFiltrePlanad(): void {
    this.filtrePlanadNumero.set('');
    this.pageJournees.set(1);
  }

  onFiltreTypeOp(ev: Event): void {
    this.filtreTypeOperation.set((ev.target as HTMLSelectElement).value);
    this.pageOperations.set(1);
  }

  onFiltreStatutOp(ev: Event): void {
    this.filtreStatutOperation.set((ev.target as HTMLSelectElement).value);
    this.pageOperations.set(1);
  }

  formatDateFr(iso: string): string {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return new Intl.DateTimeFormat('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(d);
  }

  formatVariation(n: number): string {
    if (n > 0) return '+' + formatFcfa(n);
    if (n < 0) return '−' + formatFcfa(Math.abs(n));
    return formatFcfa(0);
  }

  variationPillClass(n: number): string {
    if (n > 0) return 'vp-pos';
    if (n < 0) return 'vp-neg';
    return 'vp-neu';
  }

  variationClass(n: number): string {
    if (n > 0) return 'cr-c';
    if (n < 0) return 'cr-r';
    return 'muted';
  }

  nbOperationsActives(): number {
    return this.recap()?.synthese.nbOperationsActives ?? 0;
  }

  private todayIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
