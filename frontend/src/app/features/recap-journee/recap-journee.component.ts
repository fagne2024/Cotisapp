import { Component, computed, inject, OnInit, signal, HostListener } from '@angular/core';

import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import { paginateSlice } from '../../shared/util/pagination.util';
import { AuthService } from '../../core/services/auth.service';

import {

  JourneeReunionDto,

  RecapJourneeDto,

  RecapJourneeService,

} from '../../core/services/recap-journee.service';

import { NotificationService } from '../../core/services/notification.service';

import { formatFcfa } from '../../core/utils/currency.util';
import { DROIT_ACTION_IMPORTS } from '../../shared/imports/droit-action.imports';

import { downloadCsv } from '../../shared/util/csv-download.util';

import {

  avatarColor,

  compteOrgBoxClass,

  filtrerOperationsAvance,

  filtrerParRechercheGlobale,

  initialsFromName,

  planadBadgeFromLibelle,

  soldeDebutCompte,

  statutMembreLabel,

  typeOperationMeta,

} from './recap-journee-display.util';

import {

  filtreMembreRecapActif,

  filtrePlanadNumeroActif,

  filtrerJourneesParNumero,

  filtrerRecapMembres,

  filtrerRecapOperations,

} from './recap-journee-filtre.util';



@Component({

  selector: 'app-recap-journee',

  standalone: true,

  imports: [ListPaginationComponent, ...DROIT_ACTION_IMPORTS],

  templateUrl: './recap-journee.component.html',

  styleUrls: [
    './recap-journee.component.scss',
    '../../shared/styles/membre-search-row.scss',
    '../../shared/styles/pagination.scss',
  ],

})

export class RecapJourneeComponent implements OnInit {

  private readonly auth = inject(AuthService);

  private readonly recapService = inject(RecapJourneeService);

  private readonly notify = inject(NotificationService);



  readonly formatFcfa = formatFcfa;

  readonly initialsFromName = initialsFromName;

  readonly avatarColor = avatarColor;

  readonly planadBadgeFromLibelle = planadBadgeFromLibelle;

  readonly soldeDebutCompte = soldeDebutCompte;

  readonly typeOperationMeta = typeOperationMeta;

  readonly compteOrgBoxClass = compteOrgBoxClass;

  readonly statutMembreLabel = statutMembreLabel;



  readonly journees = signal<JourneeReunionDto[]>([]);

  readonly recap = signal<RecapJourneeDto | null>(null);

  readonly loadingListe = signal(true);

  readonly loadingRecap = signal(false);

  readonly dateSaisie = signal(this.todayIso());

  readonly journeeSelectionneeId = signal<number | null>(null);

  readonly pageJournees = signal(1);

  readonly pageComptesOrg = signal(1);

  readonly pageMembres = signal(1);

  readonly pageSynthese = signal(1);

  readonly pageOperations = signal(1);

  readonly pageSizeJournees = 10;

  readonly pageSizeComptesOrg = 3;

  readonly pageSizeSynthese = 6;

  /** Membres et opérations : 10 lignes par page (comme clôture / comptes). */
  readonly pageSizeTable = 10;

  readonly filtreMembreTexte = signal('');

  readonly filtreMembreCodeNum = signal('');

  readonly filtrePlanadNumero = signal('');

  readonly rechercheGlobale = signal('');

  readonly filtreTypeOperation = signal('');

  readonly filtreStatutOperation = signal('');

  readonly modalNommerOuvert = signal(false);



  readonly organisationNom = computed(() => this.auth.currentOrgNom() ?? 'Organisation');



  readonly filtrePlanadActif = computed(() => filtrePlanadNumeroActif(this.filtrePlanadNumero()));



  readonly journeesFiltrees = computed(() =>

    filtrerJourneesParNumero(this.journees(), this.filtrePlanadNumero())

  );

  readonly journeesPaged = computed(() =>

    paginateSlice(this.journeesFiltrees(), this.pageJournees(), this.pageSizeJournees)

  );

  readonly comptesOrgList = computed(() => this.recap()?.comptesOrganisation ?? []);

  readonly comptesOrgPaged = computed(() =>

    paginateSlice(this.comptesOrgList(), this.pageComptesOrg(), this.pageSizeComptesOrg)

  );



  readonly filtreMembreActif = computed(() =>

    filtreMembreRecapActif({

      texte: this.filtreMembreTexte(),

      codeNumero: this.filtreMembreCodeNum(),

    })

  );



  readonly membresFiltres = computed(() => {

    let list = filtrerRecapMembres(this.recap()?.membres ?? [], {

      texte: this.filtreMembreTexte(),

      codeNumero: this.filtreMembreCodeNum(),

    });

    list = filtrerParRechercheGlobale(list, this.rechercheGlobale());

    return list;

  });



  readonly operationsFiltres = computed(() => {

    let list = filtrerRecapOperations(this.recap()?.operations ?? [], {

      texte: this.filtreMembreTexte(),

      codeNumero: this.filtreMembreCodeNum(),

    });

    list = filtrerParRechercheGlobale(list, this.rechercheGlobale());

    list = filtrerOperationsAvance(list, this.filtreTypeOperation(), this.filtreStatutOperation());

    return list;

  });



  readonly membresPaged = computed(() =>

    paginateSlice(this.membresFiltres(), this.pageMembres(), this.pageSizeTable)

  );

  readonly syntheseMembresPaged = computed(() =>

    paginateSlice(this.membresFiltres(), this.pageSynthese(), this.pageSizeSynthese)

  );

  readonly operationsPaged = computed(() =>

    paginateSlice(this.operationsFiltres(), this.pageOperations(), this.pageSizeTable)

  );



  readonly nbOperationsActives = computed(() => this.recap()?.synthese.nbOperationsActives ?? 0);



  private orgId = 0;



  ngOnInit(): void {

    this.orgId = this.auth.currentOrgId() ?? 0;

    if (this.orgId < 1) return;

    this.chargerListe();

  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey) {
      if (event.key === 'f' || event.key === 'F') {
        event.preventDefault();
        // Focus on search input if available
        const searchInput = document.querySelector('input[type="search"]') as HTMLInputElement;
        if (searchInput) searchInput.focus();
      } else if (event.key === 'r' || event.key === 'R') {
        event.preventDefault();
        this.chargerListe();
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.rechercheGlobale.set('');
      this.effacerFiltreMembre();
    }
  }



  chargerListe(): void {

    this.loadingListe.set(true);

    this.recapService.lister(this.orgId).subscribe({

      next: (list) => {

        this.journees.set(list);

        this.loadingListe.set(false);

        if (list.length && this.journeeSelectionneeId() == null) {

          this.selectionnerJournee(list[0].id);

        }

      },

      error: () => {

        this.loadingListe.set(false);

        this.notify.error('Impossible de charger les journées de réunion.');

      },

    });

  }



  selectionnerJournee(id: number): void {

    this.journeeSelectionneeId.set(id);

    this.loadingRecap.set(true);

    this.recapService.obtenirRecap(this.orgId, id).subscribe({

      next: (r) => {

        this.recap.set(r);

        this.dateSaisie.set(r.dateReunion);

        this.resetPagination();

        this.loadingRecap.set(false);

      },

      error: () => {

        this.loadingRecap.set(false);

        this.notify.error('Impossible de charger le récapitulatif.');

      },

    });

  }



  chargerParDate(): void {

    const d = this.dateSaisie().trim();

    if (!d) return;

    this.loadingRecap.set(true);

    this.recapService.obtenirRecapParDate(this.orgId, d).subscribe({

      next: (r) => {

        this.recap.set(r);

        this.journeeSelectionneeId.set(r.journeeId);

        this.resetPagination();

        this.chargerListe();

        this.loadingRecap.set(false);

      },

      error: (err) => {

        this.loadingRecap.set(false);

        const m = err?.error?.message;

        this.notify.error(typeof m === 'string' ? m : 'Récapitulatif indisponible pour cette date.');

      },

    });

  }



  ouvrirModalNommer(): void {

    this.modalNommerOuvert.set(true);

  }



  fermerModalNommer(): void {

    this.modalNommerOuvert.set(false);

  }



  confirmerNommer(): void {

    this.fermerModalNommer();

    this.enregistrerJournee();

  }



  enregistrerJournee(): void {

    const d = this.dateSaisie().trim();

    if (!d) return;

    this.recapService.creer(this.orgId, d).subscribe({

      next: (j) => {

        this.notify.success(`Journée ${j.libelle} enregistrée.`);

        this.chargerListe();

        this.selectionnerJournee(j.id);

      },

      error: (err) => {

        const m = err?.error?.message;

        this.notify.error(typeof m === 'string' ? m : 'Création impossible.');

      },

    });

  }



  formatDateFr(iso: string): string {

    if (!iso) return '—';

    const d = new Date(iso + 'T12:00:00');

    const s = new Intl.DateTimeFormat('fr-FR', {

      weekday: 'long',

      day: 'numeric',

      month: 'long',

      year: 'numeric',

    }).format(d);

    return s.charAt(0).toUpperCase() + s.slice(1);

  }



  formatDateCourte(iso: string): string {

    if (!iso) return '—';

    const d = new Date(iso + 'T12:00:00');

    return new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' }).format(d);

  }



  variationClass(v: number): string {

    if (v > 0) return 'cr-c';

    if (v < 0) return 'db-c';

    return '';

  }



  variationPillClass(v: number): string {

    if (v > 0) return 'vp-pos';

    if (v < 0) return 'vp-neg';

    return 'vp-zero';

  }



  formatVariation(v: number): string {

    const sign = v > 0 ? '+' : '';

    return `${sign}${formatFcfa(v)}`;

  }



  formatMontantSigne(v: number, positifCredit = true): string {

    if (v === 0) return formatFcfa(0);

    const sign = v > 0 ? '+' : '−';

    return `${sign}${formatFcfa(Math.abs(v))}`;

  }



  impactCaisseLabel(op: {
    annulee: boolean;
    annulation: boolean;
    typeOperation: string;
    montant: number;
    montantTotal: number;
  }): string {

    if (op.annulee || op.annulation) return '—';

    if (['COTISATION', 'COTISATION_MOIS', 'VERSEMENT', 'REMBOURSEMENT'].includes(op.typeOperation)) {

      return this.formatMontantSigne(op.montantTotal, true);

    }

    if (op.typeOperation === 'EMPRUNT') {

      return this.formatMontantSigne(-(op.montant ?? 0), false);

    }

    return '—';

  }

  montantLigneRecap(op: { typeOperation: string; montant: number; montantTotal: number }): number {
    return op.typeOperation === 'EMPRUNT' ? op.montant : op.montantTotal;
  }



  compteIcon(type: string): string {

    switch (type?.toUpperCase()) {

      case 'CAISSE':

        return '💵';

      case 'SOLIDARITE':

        return '🤝';

      case 'BANQUE':

        return '🏛';

      default:

        return '🏦';

    }

  }



  imprimer(): void {

    window.print();

  }



  exporterPdf(): void {

    this.notify.info('Export PDF — utilisez l\'export CSV ou l\'impression navigateur pour l\'instant.');

  }



  exporterExcel(): void {

    this.exporterOperationsCsv();

    this.exporterMembresCsv();

  }



  onFiltrePlanadNumero(ev: Event): void {

    this.filtrePlanadNumero.set((ev.target as HTMLInputElement).value);

    this.pageJournees.set(1);

  }



  effacerFiltrePlanad(): void {

    this.filtrePlanadNumero.set('');

    this.pageJournees.set(1);

  }



  onFiltreMembreTexte(ev: Event): void {

    this.filtreMembreTexte.set((ev.target as HTMLInputElement).value);

    this.resetPagesFiltre();

  }



  onFiltreMembreCodeNum(ev: Event): void {

    this.filtreMembreCodeNum.set((ev.target as HTMLInputElement).value);

    this.resetPagesFiltre();

  }



  effacerFiltreMembre(): void {

    this.filtreMembreTexte.set('');

    this.filtreMembreCodeNum.set('');

    this.resetPagesFiltre();

  }



  onRechercheGlobale(ev: Event): void {

    this.rechercheGlobale.set((ev.target as HTMLInputElement).value);

    this.resetPagesFiltre();

  }



  onFiltreTypeOp(ev: Event): void {

    this.filtreTypeOperation.set((ev.target as HTMLSelectElement).value);

    this.resetPagesFiltre();

  }



  onFiltreStatutOp(ev: Event): void {

    this.filtreStatutOperation.set((ev.target as HTMLSelectElement).value);

    this.resetPagesFiltre();

  }



  exporterMembresCsv(): void {

    const r = this.recap();

    const list = this.membresFiltres();

    if (!r || !list.length) {

      this.notify.error('Aucun membre à exporter.');

      return;

    }

    downloadCsv(this.nomFichierExport(r, 'membres'), [

      'Code membre',

      'Nom',

      'Cotisations (FCFA)',

      'Emprunts (FCFA)',

      'Remboursements (FCFA)',

      'Variation comptes (FCFA)',

      'Nb opérations',

    ], list.map((m) => [

      m.codeMembre,

      m.membreNom,

      m.montantCotisations,

      m.montantEmprunts,

      m.montantRemboursements,

      m.variationNetComptes,

      m.nbOperations,

    ]));

    this.notify.success(`${list.length} membre(s) exporté(s).`);

  }



  exporterOperationsCsv(): void {

    const r = this.recap();

    const list = this.operationsFiltres();

    if (!r || !list.length) {

      this.notify.error('Aucune opération à exporter.');

      return;

    }

    downloadCsv(this.nomFichierExport(r, 'operations'), [

      'Type',

      'Code membre',

      'Membre',

      'Montant (FCFA)',

      'Frais (FCFA)',

      'Total (FCFA)',

      'Date',

      'Statut',

      'Observation',

    ], list.map((op) => [

      op.typeLibelle,

      op.codeMembre ?? '',

      op.membreNom ?? '',

      op.montant,

      op.montantFrais,

      op.montantTotal,

      op.dateOperation,

      this.libelleStatutOperation(op),

      op.observation ?? '',

    ]));

    this.notify.success(`${list.length} opération(s) exportée(s).`);

  }



  private resetPagination(): void {

    this.pageJournees.set(1);

    this.pageComptesOrg.set(1);

    this.pageMembres.set(1);

    this.pageSynthese.set(1);

    this.pageOperations.set(1);

    this.filtreMembreTexte.set('');

    this.filtreMembreCodeNum.set('');

    this.rechercheGlobale.set('');

    this.filtreTypeOperation.set('');

    this.filtreStatutOperation.set('');

  }



  private resetPagesFiltre(): void {

    this.pageMembres.set(1);

    this.pageSynthese.set(1);

    this.pageOperations.set(1);

  }



  private nomFichierExport(r: RecapJourneeDto, suffixe: string): string {

    const base = (r.libelle || 'recap').replace(/[^\w\-]+/g, '_');

    return `${base}_${r.dateReunion}_${suffixe}.csv`;

  }



  private libelleStatutOperation(op: { annulation: boolean; annulee: boolean }): string {

    if (op.annulation) return 'Annulation';

    if (op.annulee) return 'Annulée';

    return 'Active';

  }



  private todayIso(): string {

    const d = new Date();

    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

  }

}


