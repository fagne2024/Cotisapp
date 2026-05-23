import { Component, OnDestroy, OnInit, computed, inject, signal, HostListener } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { concatMap, from, of, Subscription } from 'rxjs';
import { catchError, finalize, map, toArray } from 'rxjs/operators';
import { AuthService } from '../../../core/services/auth.service';
import { organisationCouranteId } from '../../../core/util/org-route.util';
import {
  EmpruntDto,
  EmpruntService,
  EcheanceDto,
  RembourserRequest,
  TypeEmprunt,
} from '../../../core/services/emprunt.service';
import {
  EmpruntsReglesDto,
  RegleOperationService,
} from '../../../core/services/regle-operation.service';
import { EmpruntTypeUi, regleEmpruntEffective } from '../../../core/util/regle-emprunt.util';
import { NotificationService } from '../../../core/services/notification.service';
import { formatFcfa } from '../../../core/utils/currency.util';
import {
  badgeRetardEmprunt,
  buildRemboursementAlertes,
  calculerPenaliteRetard,
  echeanceEnRetard,
  echeanceLabelComplet,
  echeanceLabelForm,
  echeanceRestant,
  echeancesOuvertes,
  empruntEnRetard,
  empruntLabelSelect,
  formatDateFr,
  PenaliteRetardCalc,
  progressPctEmprunt,
  echeancePrioritairePourRemboursement,
  prochaineEcheanceOuverte,
  referenceEmprunt,
  remboursementBloque,
  repartitionEcheanceCaisse,
  repartirRemboursementSolidarite,
  avanceCaisseRestantEmprunt,
  montantRemboursementEffectif,
  MontantRemboursementSaisie,
  statutEcheanceLabel,
  statutEcheanceUi,
  typeEmpruntLabel,
} from '../remboursement-emprunt.util';
import { libellePenaliteEmprunt } from '../../../core/util/regle-emprunt.util';
import { matchTextQuery } from '../../../shared/util/filter.util';
import {
  RemboursementHistoriqueLigneDto,
  RemboursementPanelService,
  RemboursementPanneauDto,
} from '../../../core/services/remboursement-panel.service';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import {
  filtrerParNumeroCode,
  suffixeCodeNumerique,
} from '../../../shared/util/membre-code-lookup.util';
import { ListPaginationComponent } from '../../../shared/components/list-pagination/list-pagination.component';
import { DROIT_ACTION_IMPORTS } from '../../../shared/imports/droit-action.imports';
import {
  paginateSlice,
  paginationTotalPages,
} from '../../../shared/util/pagination.util';
import { postePourCodeMembre } from '../../membres/membres-poste.util';
import {
  MODES_PAIEMENT,
  modePaiementMobile,
  type ModePaiement,
} from '../../../shared/util/mode-paiement.util';

interface ConfirmDialogView {
  title: string;
  paragraphs: string[];
  variant: 'warn' | 'danger' | 'info';
  showCancel: boolean;
  confirmLabel: string;
}

export type RembTypeUi = 'etale' | 'solidarite' | 'caisse' | 'historique';

interface RembConfig {
  type: TypeEmprunt;
  titre: string;
  sousTitre: string;
  alerteTitre: string;
  alerteDesc: string;
  alerte: string;
  bouton: string;
  avecEcheance: boolean;
  avecFrais: boolean;
  cibleOrg: string;
  simTitre: string;
}

function libelleCompteMembre(type: TypeEmprunt): string {
  switch (type) {
    case 'SOLIDARITE':
      return 'Membre - Solidarité';
    case 'CAISSE':
      return 'Membre - Épargne hebdo';
    case 'ETALE':
      return 'Membre - Épargne mois';
  }
}

function rembUiToRegleUi(t: RembTypeUi): EmpruntTypeUi {
  if (t === 'solidarite') return 'sol';
  if (t === 'caisse') return 'caisse';
  return 'etale';
}

function rembUiDepuisTypeEmprunt(t: TypeEmprunt): RembTypeUi {
  if (t === 'SOLIDARITE') return 'solidarite';
  if (t === 'CAISSE') return 'caisse';
  return 'etale';
}

const CONFIGS: Record<Exclude<RembTypeUi, 'historique'>, RembConfig> = {
  etale: {
    type: 'ETALE',
    titre: 'Remboursement — Étalé',
    sousTitre: 'Sélectionnez le type d\'emprunt — la logique comptable s\'adapte automatiquement',
    alerteTitre: 'Emprunt Étalé — Remboursement sans frais additionnels',
    alerteDesc:
      'Les intérêts sont déjà intégrés dans le montant de chaque échéance. Ce remboursement crédite la Caisse et réduit la dette du membre. Aucun frais supplémentaire.',
    alerte: 'Le montant est prérempli depuis l\'échéance sélectionnée (intérêts inclus).',
    bouton: '✅ Valider le remboursement',
    avecEcheance: true,
    avecFrais: false,
    cibleOrg: 'Organisation · Caisse',
    simTitre: 'Prévisualisation — Remboursement Étalé',
  },
  solidarite: {
    type: 'SOLIDARITE',
    titre: 'Remboursement — Solidarité',
    sousTitre: 'Capital retourne dans le fonds Solidarité · zéro intérêt',
    alerteTitre: 'Emprunt Solidarité — Retour dans le fonds Solidarité',
    alerteDesc:
      'Le remboursement crédite le fonds Solidarité (et non la Caisse). Aucun intérêt. Le fonds se reconstitue automatiquement.',
    alerte: 'Montant libre jusqu\'au solde restant de l\'emprunt solidaire.',
    bouton: '✅ Valider — Solidarité',
    avecEcheance: false,
    avecFrais: false,
    cibleOrg: 'Organisation · Solidarité',
    simTitre: 'Prévisualisation — Remboursement Solidarité',
  },
  caisse: {
    type: 'CAISSE',
    titre: 'Remboursement — Caisse / Financement',
    sousTitre: 'Capital et frais — crédit membre + caisse',
    alerteTitre: 'Emprunt Caisse / Financement — Capital et frais',
    alerteDesc:
      'Le capital et les frais créditent le compte membre (réduction de la dette) et la Caisse. Les frais débités à l\'octroi sont ainsi soldés au remboursement.',
    alerte: 'Capital depuis l\'échéance · frais issus de l\'octroi de l\'emprunt.',
    bouton: '✅ Valider — Caisse',
    avecEcheance: true,
    avecFrais: true,
    cibleOrg: 'Organisation · Caisse',
    simTitre: 'Prévisualisation — Remboursement Caisse',
  },
};

@Component({
  selector: 'app-remboursement',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ConfirmDialogComponent, ListPaginationComponent, ...DROIT_ACTION_IMPORTS],
  templateUrl: './remboursement.component.html',
  styleUrls: [
    './remboursement.component.scss',
    '../../../shared/styles/membre-search-row.scss',
    '../../../shared/styles/membre-selection-mode.scss',
    '../../../shared/styles/pagination.scss',
  ],
})
export class RemboursementComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly empruntService = inject(EmpruntService);
  private readonly regleService = inject(RegleOperationService);
  private readonly notify = inject(NotificationService);
  private readonly panelService = inject(RemboursementPanelService);
  private sub = new Subscription();

  readonly formatFcfa = formatFcfa;
  readonly formatDateFr = formatDateFr;
  readonly echeanceLabelComplet = echeanceLabelComplet;
  readonly echeanceLabelForm = echeanceLabelForm;
  readonly echeanceRestant = echeanceRestant;
  readonly empruntEnRetard = empruntEnRetard;
  readonly empruntLabelSelect = empruntLabelSelect;
  readonly referenceEmprunt = referenceEmprunt;
  readonly statutEcheanceLabel = statutEcheanceLabel;
  readonly statutEcheanceUi = statutEcheanceUi;
  readonly badgeRetardEmprunt = badgeRetardEmprunt;
  readonly echeanceEnRetard = echeanceEnRetard;
  readonly typeEmpruntLabel = typeEmpruntLabel;
  readonly libellePenaliteEmprunt = libellePenaliteEmprunt;

  readonly typeUi = signal<RembTypeUi>('etale');
  readonly config = computed(() => {
    const t = this.typeUi();
    if (t === 'historique') return CONFIGS.etale;
    return CONFIGS[t];
  });
  readonly panneau = signal<RemboursementPanneauDto | null>(null);
  readonly panneauLoading = signal(false);

  readonly historiqueLignes = signal<RemboursementHistoriqueLigneDto[]>([]);
  readonly historiqueLoading = signal(false);
  readonly annulationOperationId = signal<number | null>(null);
  readonly filtreHistType = signal<'tous' | 'etale' | 'caisse' | 'solidarite'>('tous');
  readonly filtreHistRecherche = signal('');
  readonly membreCodeNumero = signal('');
  readonly filtreHistDateDebut = signal('');
  readonly filtreHistDateFin = signal('');
  readonly reglesEmprunt = signal<EmpruntsReglesDto | null>(null);
  readonly emprunts = signal<EmpruntDto[]>([]);
  /** Tous types — panneau latéral « emprunts actifs ». */
  readonly empruntsActifs = signal<EmpruntDto[]>([]);
  readonly empruntSelectionne = signal<EmpruntDto | null>(null);
  readonly loading = signal(false);
  readonly confirmDialog = signal<ConfirmDialogView | null>(null);
  private confirmCallback: (() => void) | null = null;
  readonly formTick = signal(0);

  readonly modeSaisieMembre = signal<'unitaire' | 'bulk'>('unitaire');
  readonly empruntsBulk = signal<EmpruntDto[]>([]);
  readonly bulkFiltre = signal('');
  readonly bulkPage = signal(1);
  readonly bulkPageSize = 10;

  readonly regleActive = computed(() =>
    regleEmpruntEffective(this.reglesEmprunt(), rembUiToRegleUi(this.typeUi()))
  );

  readonly form = this.fb.nonNullable.group({
    empruntId: [null as number | null, Validators.required],
    echeanceId: [null as number | null],
    montant: [0, [Validators.required, Validators.min(1)]],
    montantCapital: [0, Validators.min(0)],
    montantFrais: [0, Validators.min(0)],
    montantPenalite: [0, Validators.min(0)],
    appliquerPenalite: [true],
    datePaiement: [new Date().toISOString().slice(0, 10), Validators.required],
    modePaiement: ['ESPECES' as ModePaiement],
    referencePaiement: [''],
    observation: [''],
  });

  readonly modesPaiement = MODES_PAIEMENT;
  readonly modePaiementMobile = modePaiementMobile;

  readonly echeancesDisponibles = computed(() => {
    const emp = this.empruntSelectionne();
    if (!emp) return [];
    return echeancesOuvertes(emp);
  });

  readonly echeanceCourante = computed(() => {
    const emp = this.empruntSelectionne();
    if (!emp) return null;
    const id = this.form.getRawValue().echeanceId;
    if (id != null) {
      return (emp.echeances ?? []).find((e) => e.id === id) ?? null;
    }
    return prochaineEcheanceOuverte(emp);
  });

  readonly penaliteCalc = computed((): PenaliteRetardCalc => {
    this.formTick();
    const emp = this.empruntSelectionne();
    const v = this.form.getRawValue();
    if (!emp || !v.appliquerPenalite) {
      return { applicable: false, montant: 0, moisRetard: 0, base: 0 };
    }
    return calculerPenaliteRetard(emp, this.regleActive(), v.echeanceId, this.dateFromFormValue(v.datePaiement));
  });

  readonly alertes = computed(() => {
    this.formTick();
    const cfg = this.config();
    const v = this.form.getRawValue();
    return buildRemboursementAlertes({
      emp: this.empruntSelectionne(),
      regle: this.regleActive(),
      avecEcheance: cfg.avecEcheance,
      avecFrais: cfg.avecFrais,
      montant: Number(v.montant) || 0,
      montantCapital: Number(v.montantCapital) || 0,
      montantFrais: Number(v.montantFrais) || 0,
      montantPenalite: Number(v.montantPenalite) || 0,
      appliquerPenalite: v.appliquerPenalite,
      echeanceId: v.echeanceId,
      datePaiement: v.datePaiement,
    });
  });

  readonly validationBloquee = computed(() => remboursementBloque(this.alertes()));

  readonly empruntsEnRetard = computed(() => this.emprunts().filter((e) => empruntEnRetard(e)));

  readonly filtreRecherche = signal('');
  readonly filtreStatut = signal<'tous' | 'retard'>('tous');

  readonly historiqueFiltre = computed(() => {
    const q = this.filtreHistRecherche().trim().toLowerCase();
    const ft = this.filtreHistType();
    const d0 = this.filtreHistDateDebut();
    const d1 = this.filtreHistDateFin();
    return this.historiqueLignes().filter((l) => {
      if (ft !== 'tous') {
        const map: Record<string, string> = {
          etale: 'ETALE',
          caisse: 'CAISSE',
          solidarite: 'SOLIDARITE',
        };
        if (l.typeEmprunt !== map[ft]) return false;
      }
      if (d0 && l.dateOperation < d0) return false;
      if (d1 && l.dateOperation > d1) return false;
      if (!q) return true;
      const hay = [l.membreNom, l.codeMembre, l.typeLibelle, l.dateLabel, l.observation ?? '']
        .join(' ')
        .toLowerCase();
      return hay.includes(q);
    });
  });

  readonly historiqueTotaux = computed(() => {
    const rows = this.historiqueFiltre().filter((r) => !r.annulee);
    const sum = (type: string) =>
      rows.filter((r) => r.typeEmprunt === type).reduce((a, r) => a + (r.montantTotal || 0), 0);
    const count = (type: string) => rows.filter((r) => r.typeEmprunt === type).length;
    return {
      etale: count('ETALE'),
      caisse: count('CAISSE'),
      solidarite: count('SOLIDARITE'),
      montantEtale: sum('ETALE'),
      montantCaisse: sum('CAISSE'),
      montantSolidarite: sum('SOLIDARITE'),
    };
  });

  readonly avanceCaisseEmprunt = computed(() => {
    const emp = this.empruntSelectionne();
    if (!emp || emp.typeEmprunt !== 'SOLIDARITE') return null;
    const restant = avanceCaisseRestantEmprunt(emp);
    if (restant <= 0) return null;
    return {
      total: Number(emp.montantAvanceCaisse ?? 0),
      restant,
    };
  });

  readonly simProgression = computed(() => {
    const emp = this.empruntSelectionne();
    const v = this.form.getRawValue();
    if (!emp) return null;
    const cfg = this.config();
    const cap = cfg.avecFrais ? Number(v.montantCapital) || 0 : Number(v.montant) || 0;
    const frais = cfg.avecFrais ? Number(v.montantFrais) || 0 : 0;
    const penalite = v.appliquerPenalite ? Number(v.montantPenalite) || 0 : 0;
    const paye = cap + frais + penalite;
    const remb = (Number(emp.montantRembourse) || 0) + paye;
    const total = Number(emp.montantTotal) || 1;
    const pct = Math.min(100, Math.round((remb / total) * 100));
    const reste = Math.max(0, (Number(emp.montantRestant) || 0) - paye);
    const pan = this.panneau();
    if (cfg.type === 'SOLIDARITE' && emp) {
      const rep = repartirRemboursementSolidarite(emp, cap);
      const soldeCaisseApres = (pan?.soldeCaisse ?? 0) + rep.partCaisse;
      const soldeSolApres = (pan?.soldeSolidarite ?? 0) + rep.partSolidarite;
      return { pct, reste, soldeApres: soldeSolApres, soldeCaisseApres, paye, rep };
    }
    const soldeBase = pan?.soldeCaisse ?? 0;
    return { pct, reste, soldeApres: soldeBase + cap, soldeCaisseApres: null as number | null, paye, rep: null };
  });

  readonly empruntsFiltres = computed(() => {
    const q = this.filtreRecherche();
    const fs = this.filtreStatut();
    const selId = this.empruntSelectionne()?.id;
    return this.emprunts().filter((e) => {
      if (selId === e.id) return true;
      if (fs === 'retard' && !empruntEnRetard(e)) return false;
      return matchTextQuery(q, e.membreNom, e.codeMembre);
    });
  });

  readonly empruntsCatalogueBulk = computed(() => {
    const q = this.bulkFiltre().trim();
    const fs = this.filtreStatut();
    return this.emprunts().filter((e) => {
      if (fs === 'retard' && !empruntEnRetard(e)) return false;
      return matchTextQuery(q, e.membreNom, e.codeMembre);
    });
  });

  readonly bulkCatalogueTotal = computed(() => this.empruntsCatalogueBulk().length);

  readonly bulkTotalPages = computed(() =>
    paginationTotalPages(this.bulkCatalogueTotal(), this.bulkPageSize)
  );

  readonly empruntsBulkPage = computed(() =>
    paginateSlice(this.empruntsCatalogueBulk(), this.bulkPage(), this.bulkPageSize)
  );

  readonly bulkPageToutSelectionnee = computed(() => {
    const page = this.empruntsBulkPage();
    const selected = new Set(this.empruntsBulk().map((e) => e.id));
    return page.length > 0 && page.every((e) => selected.has(e.id));
  });

  readonly bulkNbEmprunts = computed(() => this.empruntsBulk().length);

  readonly bulkRecapMontants = computed(() => {
    this.formTick();
    const selection = this.empruntsBulk();
    if (!selection.length) return null;
    const cfg = this.config();
    const v = this.form.getRawValue();
    const saisie: MontantRemboursementSaisie = {
      montant: Number(v.montant) || 0,
      montantCapital: Number(v.montantCapital) || 0,
      montantFrais: Number(v.montantFrais) || 0,
      appliquerPenalite: v.appliquerPenalite,
      echeanceId: v.echeanceId,
    };
    const regle = this.regleActive();
    const datePaiement = v.datePaiement;
    const montants = selection.map((e) =>
      montantRemboursementEffectif(e, cfg, saisie, regle, datePaiement)
    );
    const total = montants.reduce((a, b) => a + b, 0);
    const ref = montants[0] ?? 0;
    const identiques = montants.every((m) => Math.abs(m - ref) < 0.01);
    return {
      count: selection.length,
      total,
      parOperation: identiques ? ref : null,
      identiques,
    };
  });

  readonly previewLines = computed(() => {
    const cfg = this.config();
    const emp = this.empruntSelectionne();
    const v = this.form.getRawValue();
    if (!emp) return [];

    const lines: { libelle: string; montant: number; sens: string }[] = [];
    const capital = cfg.avecFrais ? Number(v.montantCapital) || 0 : Number(v.montant) || 0;
    const frais = cfg.avecFrais ? Number(v.montantFrais) || 0 : 0;

    if (cfg.type === 'SOLIDARITE' && capital > 0) {
      const rep = repartirRemboursementSolidarite(emp, capital);
      if (rep.partCaisse > 0) {
        lines.push({
          libelle: `Compte Solidarité membre — part Caisse (avance)`,
          montant: rep.partCaisse,
          sens: 'CREDIT',
        });
        lines.push({
          libelle: 'Caisse organisation (restitution avance)',
          montant: rep.partCaisse,
          sens: 'CREDIT',
        });
      }
      if (rep.partSolidarite > 0) {
        lines.push({
          libelle: `Compte Solidarité membre (${emp.membreNom})`,
          montant: rep.partSolidarite,
          sens: 'CREDIT',
        });
        lines.push({
          libelle: 'Fonds Solidarité organisation',
          montant: rep.partSolidarite,
          sens: 'CREDIT',
        });
      }
    } else {
      lines.push({
        libelle: `${libelleCompteMembre(cfg.type)} (${emp.membreNom})`,
        montant: capital,
        sens: 'CREDIT',
      });
      lines.push({
        libelle: cfg.cibleOrg,
        montant: capital,
        sens: 'CREDIT',
      });
    }
    if (frais > 0) {
      lines.push({
        libelle: `${libelleCompteMembre(cfg.type)} — frais (${emp.membreNom})`,
        montant: frais,
        sens: 'CREDIT',
      });
      lines.push({
        libelle: 'Caisse organisation (frais)',
        montant: frais,
        sens: 'CREDIT',
      });
    }
    const montantPaiement = capital + frais;
    const dejaRemb = Number(emp.montantRembourse) || 0;
    const totalDu = Number(emp.montantTotal) || 0;
    const fraisEmprunt = Number(emp.montantFrais) || 0;
    const empruntSoldeApres =
      fraisEmprunt > 0 && montantPaiement > 0 && dejaRemb + montantPaiement >= totalDu - 0.01;
    if (empruntSoldeApres) {
      lines.push({
        libelle: 'Caisse organisation (clôture frais emprunt)',
        montant: fraisEmprunt,
        sens: 'DEBIT',
      });
      lines.push({
        libelle: 'Organisation · Compte intérêts (emprunt soldé)',
        montant: fraisEmprunt,
        sens: 'CREDIT',
      });
    }
    const penalite = v.appliquerPenalite ? Number(v.montantPenalite) || 0 : 0;
    if (penalite > 0) {
      lines.push({
        libelle: `${libelleCompteMembre(cfg.type)} (pénalité retard)`,
        montant: penalite,
        sens: 'CREDIT',
      });
      lines.push({
        libelle: 'Organisation · Compte amendes & pénalités',
        montant: penalite,
        sens: 'CREDIT',
      });
    }
    return lines;
  });

  private orgId = 0;

  ngOnInit(): void {
    this.orgId = organisationCouranteId(this.route, this.auth) ?? 0;

    const initial = this.resolveVariant();
    this.typeUi.set(initial);

    this.sub.add(
      this.regleService.obtenirEmprunts(this.orgId).subscribe({
        next: (dto) => {
          this.reglesEmprunt.set(dto);
          if (this.empruntSelectionne()) {
            this.appliquerPenaliteAuto();
            this.formTick.update((n) => n + 1);
          }
        },
        error: () => this.reglesEmprunt.set(null),
      })
    );

    this.sub.add(this.form.valueChanges.subscribe(() => this.formTick.update((n) => n + 1)));

    this.appliquerValidateursFormulaire();
    this.chargerPanneau();
    this.chargerEmpruntsActifs();
    this.chargerSelonType();

    this.sub.add(
      this.route.queryParamMap.subscribe(() => {
        const variant = this.resolveVariant();
        if (variant !== this.typeUi()) {
          this.typeUi.set(variant);
          this.appliquerValidateursFormulaire();
          this.chargerSelonType();
        } else {
          if (variant !== 'historique') {
            this.appliquerEmpruntDepuisUrl();
          }
        }
      })
    );

    this.sub.add(
      this.route.paramMap.subscribe(() => {
        const variant = this.resolveVariant();
        if (variant !== this.typeUi()) {
          this.typeUi.set(variant);
          this.appliquerValidateursFormulaire();
          this.chargerSelonType();
        }
      })
    );
  }

  private chargerSelonType(): void {
    if (this.typeUi() === 'historique') {
      this.empruntSelectionne.set(null);
      this.chargerHistorique();
    } else {
      this.chargerEmprunts();
    }
  }

  private chargerEmpruntsActifs(): void {
    if (this.orgId < 1) return;
    this.empruntService.lister(this.orgId).subscribe({
      next: (list) => this.empruntsActifs.set(list.filter((e) => e.statut === 'EN_COURS')),
      error: () => this.empruntsActifs.set([]),
    });
  }

  chargerPanneau(): void {
    if (this.orgId < 1) return;
    this.panneauLoading.set(true);
    this.panelService.chargerPanneau(this.orgId).subscribe({
      next: (p) => {
        this.panneau.set(p);
        this.panneauLoading.set(false);
      },
      error: () => {
        this.panneau.set(null);
        this.panneauLoading.set(false);
      },
    });
  }

  chargerHistorique(): void {
    if (this.orgId < 1) return;
    this.historiqueLoading.set(true);
    this.panelService.listerHistorique(this.orgId).subscribe({
      next: (lignes) => {
        this.historiqueLignes.set(
          (lignes ?? []).map((l) => ({
            ...l,
            dateOperation: this.dateOperationIso(l.dateOperation),
            annulee: !!l.annulee,
            annulable: !!l.annulable,
          }))
        );
        this.historiqueLoading.set(false);
      },
      error: (err) => {
        this.historiqueLignes.set([]);
        this.historiqueLoading.set(false);
        const msg = err?.error?.message;
        this.notify.error(
          typeof msg === 'string' ? msg : 'Impossible de charger l\'historique des remboursements.'
        );
      },
    });
  }

  private dateOperationIso(value: string | unknown): string {
    if (typeof value === 'string' && value.length >= 10) {
      return value.slice(0, 10);
    }
    if (Array.isArray(value) && value.length >= 3) {
      const y = value[0];
      const m = String(value[1]).padStart(2, '0');
      const d = String(value[2]).padStart(2, '0');
      return `${y}-${m}-${d}`;
    }
    return '';
  }

  onFiltreHistType(ev: Event): void {
    const v = (ev.target as HTMLSelectElement).value;
    if (v === 'etale' || v === 'caisse' || v === 'solidarite' || v === 'tous') {
      this.filtreHistType.set(v);
    }
  }

  onFiltreHistRecherche(ev: Event): void {
    this.filtreHistRecherche.set((ev.target as HTMLInputElement).value);
  }

  onFiltreHistDateDebut(ev: Event): void {
    this.filtreHistDateDebut.set((ev.target as HTMLInputElement).value);
  }

  onFiltreHistDateFin(ev: Event): void {
    this.filtreHistDateFin.set((ev.target as HTMLInputElement).value);
  }

  reinitialiserFiltreHistDates(): void {
    this.filtreHistDateDebut.set('');
    this.filtreHistDateFin.set('');
  }

  estAnnulationEnCours(operationId: number): boolean {
    return this.annulationOperationId() === operationId;
  }

  confirmerAnnulationRemboursement(h: RemboursementHistoriqueLigneDto): void {
    if (!h.annulable || h.annulee || this.orgId < 1) return;
    this.ouvrirConfirmDialog(
      {
        title: 'Annuler le remboursement',
        paragraphs: [
          `Annuler le remboursement de ${h.membreNom} (+${formatFcfa(h.montantTotal)}) ?`,
          'Les écritures seront inversées et le suivi de l\'emprunt sera recalculé.',
          'Seul le dernier remboursement de chaque emprunt peut être annulé.',
        ],
        variant: 'danger',
        showCancel: true,
        confirmLabel: 'Confirmer l\'annulation',
      },
      () => this.executerAnnulationRemboursement(h)
    );
  }

  private executerAnnulationRemboursement(h: RemboursementHistoriqueLigneDto): void {
    this.annulationOperationId.set(h.operationId);
    this.panelService.annulerOperation(this.orgId, h.operationId).subscribe({
      next: (res) => {
        this.annulationOperationId.set(null);
        this.notify.success(res.message ?? 'Remboursement annulé.');
        this.chargerHistorique();
        this.chargerPanneau();
        if (this.typeUi() !== 'historique') {
          const cfg = this.config();
          this.empruntService.lister(this.orgId, cfg.type).subscribe({
            next: (list) => {
              this.emprunts.set(list);
              const sel = this.empruntSelectionne()?.id;
              if (sel) {
                const emp = list.find((e) => e.id === sel);
                if (emp) this.selectEmprunt(emp.id, { updateUrl: false });
              }
            },
          });
        }
      },
      error: (err) => {
        this.annulationOperationId.set(null);
        const m = err?.error?.message;
        this.notify.error(typeof m === 'string' ? m : 'Annulation impossible.');
      },
    });
  }

  selectEmpruntDepuisPanneau(emp: EmpruntDto): void {
    const t = rembUiDepuisTypeEmprunt(emp.typeEmprunt);
    if (t !== this.typeUi() && this.typeUi() !== 'historique') {
      void this.router.navigate(['/organisations', this.orgId, 'operations', 'remboursements'], {
        queryParams: { t, empruntId: emp.id },
      });
      return;
    }
    if (this.typeUi() === 'historique') {
      void this.router.navigate(['/organisations', this.orgId, 'operations', 'remboursements'], {
        queryParams: { t, empruntId: emp.id },
      });
      return;
    }
    this.selectEmprunt(emp.id);
  }

  private empruntIdDepuisUrl(): number | null {
    const raw = this.route.snapshot.queryParamMap.get('empruntId');
    if (!raw) return null;
    const id = Number(raw);
    return Number.isFinite(id) ? id : null;
  }

  private appliquerValidateursFormulaire(): void {
    const cfg = this.config();
    const { montant, montantCapital, montantFrais } = this.form.controls;
    if (cfg.avecFrais) {
      montant.clearValidators();
      montantCapital.setValidators([Validators.required, Validators.min(1)]);
      montantFrais.setValidators([Validators.required, Validators.min(0)]);
    } else {
      montant.setValidators([Validators.required, Validators.min(1)]);
      montantCapital.clearValidators();
      montantFrais.clearValidators();
    }
    montant.updateValueAndValidity({ emitEvent: false });
    montantCapital.updateValueAndValidity({ emitEvent: false });
    montantFrais.updateValueAndValidity({ emitEvent: false });
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey) {
      if (event.key === '1') {
        event.preventDefault();
        this.typeUi.set('etale');
      } else if (event.key === '2') {
        event.preventDefault();
        this.typeUi.set('solidarite');
      } else if (event.key === '3') {
        event.preventDefault();
        this.typeUi.set('caisse');
      } else if (event.key === '4') {
        event.preventDefault();
        this.typeUi.set('historique');
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.effacerFiltresEmprunts();
    }
  }

  orgCourante(): number {
    return this.orgId;
  }

  onFiltreRecherche(ev: Event): void {
    this.filtreRecherche.set((ev.target as HTMLInputElement).value);
  }

  onFiltreStatut(ev: Event): void {
    const v = (ev.target as HTMLSelectElement).value;
    this.filtreStatut.set(v === 'retard' ? 'retard' : 'tous');
  }

  effacerFiltresEmprunts(): void {
    this.filtreRecherche.set('');
    this.filtreStatut.set('tous');
    this.membreCodeNumero.set('');
  }

  setType(t: RembTypeUi): void {
    if (t === this.typeUi()) return;
    this.empruntSelectionne.set(null);
    this.filtreRecherche.set('');
    this.filtreStatut.set('tous');
    this.membreCodeNumero.set('');
    void this.router.navigate(['/organisations', this.orgId, 'operations', 'remboursements'], {
      queryParams: { t, empruntId: t === 'historique' ? null : undefined },
      queryParamsHandling: 'merge',
    });
  }

  private resolveVariant(): RembTypeUi {
    const q = this.route.snapshot.queryParamMap.get('t');
    if (q === 'etale' || q === 'solidarite' || q === 'caisse' || q === 'historique') {
      return q;
    }
    const data = this.route.snapshot.data['variant'] as string | undefined;
    if (data === 'etale' || data === 'solidarite' || data === 'caisse') {
      return data;
    }
    const url = this.router.url;
    if (url.includes('solidarite')) return 'solidarite';
    if (url.includes('caisse')) return 'caisse';
    return 'etale';
  }

  private appliquerEmpruntDepuisUrl(): void {
    const id = this.empruntIdDepuisUrl();
    if (id == null) return;
    if (this.empruntSelectionne()?.id === id) return;
    const emp = this.emprunts().find((e) => e.id === id);
    if (emp) this.selectEmprunt(id, { updateUrl: false });
  }

  private chargerEmprunts(): void {
    const cfg = this.config();
    const preferId = this.empruntIdDepuisUrl();
    this.empruntService.lister(this.orgId, cfg.type).subscribe({
      next: (list) => {
        this.emprunts.set(list);
        if (preferId != null) {
          const inList = list.find((e) => e.id === preferId);
          if (inList) {
            this.selectEmprunt(preferId, { updateUrl: false });
          } else {
            this.chargerEmpruntParIdEtBasculerType(preferId);
          }
        } else {
          this.reinitialiserFormulaireSansEmprunt();
        }
      },
      error: () => {
        this.emprunts.set([]);
        this.reinitialiserFormulaireSansEmprunt();
        this.notify.error('Impossible de charger les emprunts.');
      },
    });
  }

  private chargerEmpruntParIdEtBasculerType(empruntId: number): void {
    this.empruntService.obtenir(this.orgId, empruntId).subscribe({
      next: (emp) => {
        const tEmp = rembUiDepuisTypeEmprunt(emp.typeEmprunt);
        const tUrl = this.typeUi();
        const tParam = this.route.snapshot.queryParamMap.get('t');

        if (tEmp !== tUrl) {
          if (tParam == null) {
            void this.router.navigate(['/organisations', this.orgId, 'operations', 'remboursements'], {
              queryParams: { t: tEmp, empruntId },
              queryParamsHandling: 'merge',
            });
            return;
          }
          void this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { empruntId: null },
            queryParamsHandling: 'merge',
            replaceUrl: true,
          });
          this.reinitialiserFormulaireSansEmprunt();
          return;
        }

        this.emprunts.update((list) => (list.some((e) => e.id === emp.id) ? list : [...list, emp]));
        this.selectEmprunt(emp.id, { updateUrl: false });
      },
      error: () => {
        this.reinitialiserFormulaireSansEmprunt();
        this.notify.error('Emprunt introuvable ou non remboursable pour cet onglet.');
      },
    });
  }

  private reinitialiserFormulaireSansEmprunt(): void {
    this.empruntSelectionne.set(null);
    this.form.reset({
      empruntId: null,
      echeanceId: null,
      montant: 0,
      montantCapital: 0,
      montantFrais: 0,
      montantPenalite: 0,
      appliquerPenalite: true,
      datePaiement: this.todayIso(),
      modePaiement: 'ESPECES',
      observation: '',
    });
    this.formTick.update((n) => n + 1);
  }

  selectEmprunt(id: number, options?: { updateUrl?: boolean }): void {
    const updateUrl = options?.updateUrl !== false;
    if (updateUrl) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { empruntId: id },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }

    const local = this.emprunts().find((e) => e.id === id);
    if (local) {
      this.preremplirDepuisEmprunt(local);
    }

    this.empruntService.obtenir(this.orgId, id).subscribe({
      next: (emp) => {
        this.emprunts.update((list) => list.map((e) => (e.id === emp.id ? emp : e)));
        this.preremplirDepuisEmprunt(emp);
      },
      error: () => {
        if (!local) {
          this.notify.error('Impossible de charger le détail de l\'emprunt.');
        }
      },
    });
  }

  rechercherEmpruntParCodeNumero(): void {
    const numero = this.membreCodeNumero().trim();
    if (!numero) return;
    const matches = filtrerParNumeroCode(this.emprunts(), numero);
    if (matches.length === 1) {
      this.selectEmprunt(matches[0].id);
      return;
    }
    if (matches.length > 1) {
      this.filtreRecherche.set(numero);
      this.selectEmprunt(matches[0].id);
      return;
    }
    this.notify.error('Aucun emprunt en cours pour ce numéro de code.');
  }

  onMembreCodeNumeroInput(event: Event): void {
    const v = (event.target as HTMLInputElement).value;
    this.membreCodeNumero.set(v);
    const n = v.trim();
    if (n.length >= 1) {
      const matches = filtrerParNumeroCode(this.emprunts(), n);
      if (matches.length === 1) {
        this.selectEmprunt(matches[0].id);
      } else if (matches.length > 1) {
        this.filtreRecherche.set(n);
      }
    }
  }

  onMembreCodeNumeroEnter(event: Event): void {
    event.preventDefault();
    this.rechercherEmpruntParCodeNumero();
  }

  private preremplirDepuisEmprunt(emp: EmpruntDto): void {
    this.empruntSelectionne.set(emp);
    if (emp.codeMembre) {
      this.membreCodeNumero.set(suffixeCodeNumerique(emp.codeMembre));
    }
    const cfg = this.config();
    if (cfg.avecEcheance && echeancesOuvertes(emp).length) {
      const ech = echeancePrioritairePourRemboursement(emp)!;
      this.appliquerEcheance(emp, ech);
    } else {
      const rest = Math.max(0, Number(emp.montantRestant) || 0);
      const frais = Math.max(0, Number(emp.montantFrais) || 0);
      this.form.patchValue({
        empruntId: emp.id,
        echeanceId: null,
        montant: rest,
        montantCapital: rest,
        montantFrais: cfg.avecFrais ? frais : 0,
        datePaiement: this.todayIso(),
        modePaiement: 'ESPECES',
        appliquerPenalite: true,
      });
    }
    this.appliquerPenaliteAuto();
    this.form.markAsPristine();
    this.form.updateValueAndValidity();
    this.formTick.update((n) => n + 1);
  }

  onEmpruntSelectChange(ev: Event): void {
    const id = Number((ev.target as HTMLSelectElement).value);
    if (Number.isFinite(id) && id > 0) {
      this.selectEmprunt(id, { updateUrl: true });
    }
  }

  onEcheanceSelectChange(ev: Event): void {
    const id = Number((ev.target as HTMLSelectElement).value);
    if (!Number.isFinite(id)) return;
    const emp = this.empruntSelectionne();
    const ech = this.echeancesDisponibles().find((x) => x.id === id);
    if (emp && ech) this.appliquerEcheance(emp, ech);
  }

  onEcheanceIdChange(id: number): void {
    const emp = this.empruntSelectionne();
    const ech = this.echeancesDisponibles().find((x) => x.id === id);
    if (emp && ech) this.appliquerEcheance(emp, ech);
  }

  private todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private dateFromFormValue(value: Date | string | null | undefined): Date {
    if (value instanceof Date) return value;
    if (typeof value === 'string' && value.length >= 10) {
      return new Date(value.slice(0, 10) + 'T12:00:00');
    }
    return new Date();
  }

  private appliquerEcheance(emp: EmpruntDto, ech: EcheanceDto, datePaiement = this.todayIso()): void {
    const cfg = this.config();
    const parts = cfg.avecFrais ? repartitionEcheanceCaisse(emp, ech) : null;
    const rest = parts?.totalRestant ?? echeanceRestant(ech);
    this.form.patchValue({
      empruntId: emp.id,
      echeanceId: ech.id,
      montant: rest,
      montantCapital: parts ? parts.capitalRestant : rest,
      montantFrais: parts ? parts.fraisRestant : 0,
      datePaiement,
      modePaiement: 'ESPECES',
      appliquerPenalite: true,
    });
    this.appliquerPenaliteAuto();
    this.formTick.update((n) => n + 1);
  }

  private appliquerPenaliteAuto(): void {
    const emp = this.empruntSelectionne();
    const v = this.form.getRawValue();
    if (!emp || !v.appliquerPenalite) {
      this.form.patchValue({ montantPenalite: 0 }, { emitEvent: false });
      return;
    }
    const p = calculerPenaliteRetard(
      emp,
      this.regleActive(),
      v.echeanceId,
      this.dateFromFormValue(v.datePaiement)
    );
    this.form.patchValue({ montantPenalite: p.montant }, { emitEvent: false });
  }

  appliquerPenaliteSuggeree(): void {
    this.form.patchValue({ appliquerPenalite: true });
    this.appliquerPenaliteAuto();
    this.formTick.update((n) => n + 1);
  }

  onAppliquerPenaliteChange(checked: boolean): void {
    this.form.patchValue({ appliquerPenalite: checked });
    if (checked) {
      this.appliquerPenaliteAuto();
    } else {
      this.form.patchValue({ montantPenalite: 0 });
    }
    this.formTick.update((n) => n + 1);
  }

  onDatePaiementChange(): void {
    this.appliquerPenaliteAuto();
    this.formTick.update((n) => n + 1);
  }

  appliquerMontantExactEcheance(): void {
    const emp = this.empruntSelectionne();
    const ech = this.echeanceCourante();
    if (!emp || !ech) return;
    const cfg = this.config();
    if (cfg.avecFrais) {
      const parts = repartitionEcheanceCaisse(emp, ech);
      this.form.patchValue({
        montant: parts.totalRestant,
        montantCapital: parts.capitalRestant,
        montantFrais: parts.fraisRestant,
      });
    } else {
      const rest = echeanceRestant(ech);
      this.form.patchValue({ montant: rest });
    }
    this.formTick.update((n) => n + 1);
  }

  empruntLabel(e: EmpruntDto): string {
    return empruntLabelSelect(e);
  }

  progressPct(emp: EmpruntDto): number {
    return progressPctEmprunt(emp);
  }

  statutEcheanceClass(ech: EcheanceDto): string {
    return statutEcheanceUi(ech);
  }

  echeanceSelectionnee(ech: EcheanceDto): boolean {
    return this.form.controls.echeanceId.value === ech.id;
  }

  peutChoisirEcheance(ech: EcheanceDto): boolean {
    return ech.statut !== 'PAYE';
  }

  choisirEcheance(ech: EcheanceDto, event?: Event): void {
    event?.stopPropagation();
    if (!this.peutChoisirEcheance(ech)) return;
    this.onEcheanceIdChange(ech.id);
  }

  annuler(): void {
    const emp = this.empruntSelectionne();
    if (emp) this.selectEmprunt(emp.id, { updateUrl: false });
  }

  setModeSaisieMembre(mode: 'unitaire' | 'bulk'): void {
    if (this.modeSaisieMembre() === mode) return;
    this.modeSaisieMembre.set(mode);
    const ctl = this.form.controls.empruntId;
    if (mode === 'bulk') {
      ctl.clearValidators();
      ctl.setValue(null, { emitEvent: false });
      this.empruntSelectionne.set(null);
      this.empruntsBulk.set([]);
      this.bulkFiltre.set('');
      this.bulkPage.set(1);
    } else {
      ctl.setValidators(Validators.required);
      this.empruntsBulk.set([]);
      this.bulkFiltre.set('');
      this.bulkPage.set(1);
      const first = this.empruntsFiltres()[0];
      if (first) {
        this.selectEmprunt(first.id, { updateUrl: false });
      }
    }
    ctl.updateValueAndValidity({ emitEvent: false });
    this.formTick.update((n) => n + 1);
  }

  onBulkFiltreInput(event: Event): void {
    this.bulkFiltre.set((event.target as HTMLInputElement).value ?? '');
    this.bulkPage.set(1);
  }

  goBulkPage(p: number): void {
    this.bulkPage.set(Math.min(this.bulkTotalPages(), Math.max(1, p)));
  }

  toggleSelectionPageBulk(): void {
    const page = this.empruntsBulkPage();
    if (this.bulkPageToutSelectionnee()) {
      const ids = new Set(page.map((e) => e.id));
      this.empruntsBulk.update((list) => list.filter((e) => !ids.has(e.id)));
    } else {
      const existants = new Set(this.empruntsBulk().map((e) => e.id));
      const ajouts = page.filter((e) => !existants.has(e.id));
      if (ajouts.length > 0) {
        this.empruntsBulk.update((list) => [...list, ...ajouts]);
      }
    }
    this.formTick.update((n) => n + 1);
  }

  isEmpruntBulkSelected(id: number): boolean {
    return this.empruntsBulk().some((e) => e.id === id);
  }

  toggleEmpruntBulk(e: EmpruntDto): void {
    if (this.isEmpruntBulkSelected(e.id)) {
      this.empruntsBulk.update((list) => list.filter((x) => x.id !== e.id));
    } else {
      this.empruntsBulk.update((list) => [...list, e]);
    }
    this.formTick.update((n) => n + 1);
  }

  retirerEmpruntBulk(id: number): void {
    this.empruntsBulk.update((list) => list.filter((e) => e.id !== id));
    this.formTick.update((n) => n + 1);
  }

  viderEmpruntsBulk(): void {
    this.empruntsBulk.set([]);
    this.formTick.update((n) => n + 1);
  }

  montantRemboursementEffectifPourEmprunt(emp: EmpruntDto): number {
    const cfg = this.config();
    const v = this.form.getRawValue();
    return montantRemboursementEffectif(
      emp,
      cfg,
      {
        montant: Number(v.montant) || 0,
        montantCapital: Number(v.montantCapital) || 0,
        montantFrais: Number(v.montantFrais) || 0,
        appliquerPenalite: v.appliquerPenalite,
        echeanceId: v.echeanceId,
      },
      this.regleActive(),
      v.datePaiement
    );
  }

  initials(nom: string): string {
    return nom
      .split(' ')
      .filter(Boolean)
      .map((p) => p[0])
      .join('')
      .slice(0, 2)
      .toUpperCase();
  }

  avatarColor(code: string): string {
    const k = postePourCodeMembre(code).kind;
    if (k === 'president') return '#7c3aed';
    if (k === 'sg' || k === 'sga') return '#1e6fa8';
    if (k === 'tresorier') return '#c0392b';
    return 'var(--g2)';
  }

  valider(): void {
    if (this.modeSaisieMembre() === 'bulk') {
      this.validerBulk();
      return;
    }
    if (this.form.invalid || this.validationBloquee()) {
      this.form.markAllAsTouched();
      const err = this.alertes().find((a) => a.level === 'error');
      if (err) this.notify.error(err.message);
      return;
    }
    const empruntId = this.form.value.empruntId;
    if (!empruntId) return;

    const cfg = this.config();
    const emp = this.empruntSelectionne();
    if (cfg.type === 'SOLIDARITE' && emp) {
      const montant = Number(this.form.getRawValue().montant) || 0;
      const rep = repartirRemboursementSolidarite(emp, montant);
      if (rep.partCaisse > 0) {
        this.ouvrirConfirmDialog(
          {
            title: 'Remboursement — ventilation Caisse / Solidarité',
            paragraphs: [
              `Sur ${formatFcfa(montant)} : ${formatFcfa(rep.partCaisse)} seront restitués à la Caisse (avance octroi) et ${formatFcfa(rep.partSolidarite)} au fonds Solidarité.`,
              'La Caisse est recréditée du même montant qu\'avait été débité à l\'octroi (sans mouvement sur Solidarité pour cette part).',
              rep.partSolidarite > 0
                ? `Le fonds Solidarité sera crédité de ${formatFcfa(rep.partSolidarite)}.`
                : 'Aucun crédit sur le fonds Solidarité pour ce paiement.',
            ],
            variant: 'warn',
            showCancel: true,
            confirmLabel: 'Confirmer le remboursement',
          },
          () => this.executerRemboursement()
        );
        return;
      }
    }

    this.executerRemboursement();
  }

  private validerBulk(): void {
    const selection = this.empruntsBulk();
    if (selection.length === 0) {
      this.notify.error('Sélectionnez au moins un emprunt.');
      return;
    }
    if (this.form.controls.datePaiement.invalid) {
      this.notify.error('Vérifiez la date du paiement.');
      return;
    }
    if (
      modePaiementMobile(this.form.controls.modePaiement.value) &&
      !this.referencePaiementPourRequete()
    ) {
      this.notify.error('Indiquez le n° de transaction Wave ou Orange Money.');
      return;
    }
    const cfg = this.config();
    const montant = Number(this.form.getRawValue().montant) || 0;
    if (montant < 1 && !cfg.avecFrais) {
      this.notify.error('Montant invalide.');
      return;
    }
    if (cfg.avecFrais) {
      const cap = Number(this.form.getRawValue().montantCapital) || 0;
      if (cap < 1) {
        this.notify.error('Montant capital invalide.');
        return;
      }
    }

    this.loading.set(true);
    from(selection)
      .pipe(
        concatMap((emp) => {
          const body = this.buildRequestPourEmprunt(emp, cfg);
          return this.empruntService.rembourser(this.orgId, emp.id, cfg.type, body).pipe(
            map(() => ({ emprunt: emp, ok: true as const })),
            catchError((err) =>
              of({
                emprunt: emp,
                ok: false as const,
                message:
                  typeof err?.error?.message === 'string'
                    ? err.error.message
                    : 'Erreur lors du remboursement.',
              })
            )
          );
        }),
        toArray(),
        finalize(() => this.loading.set(false))
      )
      .subscribe((results) => {
        const ok = results.filter((r) => r.ok);
        const ko = results.filter((r) => !r.ok);
        this.chargerPanneau();
        this.chargerEmpruntsActifs();
        this.empruntService.lister(this.orgId, cfg.type).subscribe({
          next: (list) => {
            this.emprunts.set(list);
            if (ok.length > 0 && ko.length === 0) {
              this.empruntsBulk.set([]);
              if (list[0]) this.selectEmprunt(list[0].id, { updateUrl: false });
            } else if (ko.length > 0) {
              const koIds = new Set(ko.map((r) => r.emprunt.id));
              this.empruntsBulk.set(selection.filter((e) => koIds.has(e.id)));
            }
          },
        });
        if (ko.length === 0) {
          this.notify.success(
            `${ok.length} remboursement${ok.length > 1 ? 's' : ''} enregistré${ok.length > 1 ? 's' : ''}.`
          );
          return;
        }
        if (ok.length > 0) {
          const codes = ko.map((r) => r.emprunt.codeMembre).join(', ');
          this.notify.show(
            `${ok.length} enregistré(s), ${ko.length} échec(s) (${codes}). Les emprunts en échec restent sélectionnés.`
          );
          return;
        }
        const first = ko[0];
        this.notify.error(
          `Aucun remboursement enregistré. ${first.emprunt.codeMembre} : ${first.ok ? 'erreur' : (first.message ?? 'erreur')}.`
        );
      });
  }

  private executerRemboursement(): void {
    const empruntId = this.form.value.empruntId;
    if (!empruntId) return;

    this.loading.set(true);
    const cfg = this.config();
    const emp = this.empruntSelectionne();
    const body = emp ? this.buildRequestPourEmprunt(emp, cfg) : this.buildRequest(cfg);
    this.empruntService.rembourser(this.orgId, empruntId, cfg.type, body).subscribe({
      next: () => {
        this.loading.set(false);
        this.notify.success('Remboursement enregistré avec succès.');
        this.chargerPanneau();
        this.chargerEmpruntsActifs();
        this.empruntService.lister(this.orgId, cfg.type).subscribe({
          next: (list) => {
            this.emprunts.set(list);
            const same = list.find((e) => e.id === empruntId) ?? list[0];
            if (same) this.selectEmprunt(same.id);
            else this.empruntSelectionne.set(null);
          },
        });
      },
      error: (err) => {
        this.loading.set(false);
        this.notify.error(err?.error?.message ?? 'Erreur lors du remboursement.');
      },
    });
  }

  ouvrirConfirmDialog(view: ConfirmDialogView, onConfirm: () => void): void {
    this.confirmCallback = onConfirm;
    this.confirmDialog.set(view);
  }

  fermerConfirmDialog(): void {
    this.confirmDialog.set(null);
    this.confirmCallback = null;
  }

  onConfirmDialogOk(): void {
    const cb = this.confirmCallback;
    this.fermerConfirmDialog();
    cb?.();
  }

  onConfirmDialogCancel(): void {
    this.fermerConfirmDialog();
  }

  private buildRequest(cfg: RembConfig): RembourserRequest {
    const emp = this.empruntSelectionne();
    if (emp) {
      return this.buildRequestPourEmprunt(emp, cfg);
    }
    return this.buildRequestDepuisFormulaire(cfg, null, null);
  }

  private buildRequestPourEmprunt(emp: EmpruntDto, cfg: RembConfig): RembourserRequest {
    const v = this.form.getRawValue();
    let echeanceId: number | null = v.echeanceId;
    let ech: EcheanceDto | null = null;
    if (cfg.avecEcheance) {
      ech =
        (echeanceId != null ? (emp.echeances ?? []).find((e) => e.id === echeanceId) : null) ??
        echeancePrioritairePourRemboursement(emp);
      echeanceId = ech?.id ?? null;
    }
    return this.buildRequestDepuisFormulaire(cfg, emp, ech);
  }

  private buildRequestDepuisFormulaire(
    cfg: RembConfig,
    emp: EmpruntDto | null,
    ech: EcheanceDto | null
  ): RembourserRequest {
    const v = this.form.getRawValue();
    const req: RembourserRequest = {
      datePaiement:
        typeof v.datePaiement === 'string'
          ? v.datePaiement.slice(0, 10)
          : this.dateFromFormValue(v.datePaiement).toISOString().slice(0, 10),
      modePaiement: v.modePaiement,
      referencePaiement: this.referencePaiementPourRequete(),
      observation: (v.observation ?? '').trim() || undefined,
      appliquerPenalite: v.appliquerPenalite,
    };
    if (v.appliquerPenalite && emp) {
      const p = calculerPenaliteRetard(
        emp,
        this.regleActive(),
        ech?.id ?? v.echeanceId,
        this.dateFromFormValue(v.datePaiement)
      );
      if (p.montant > 0) {
        req.montantPenalite = p.montant;
      }
    } else if (v.appliquerPenalite && (Number(v.montantPenalite) || 0) > 0) {
      req.montantPenalite = Number(v.montantPenalite);
    }
    if (cfg.avecEcheance && ech) {
      req.echeanceId = ech.id;
      if (cfg.avecFrais) {
        const parts = repartitionEcheanceCaisse(emp!, ech);
        const capC = Math.min(Number(v.montantCapital) || parts.capitalRestant, parts.capitalRestant);
        const capF = Math.min(Number(v.montantFrais) || 0, parts.fraisRestant);
        req.montantCapital = capC > 0 ? capC : parts.capitalRestant;
        req.montantFrais = capF;
      } else {
        const rest = echeanceRestant(ech);
        const m = Math.min(Number(v.montant) || rest, rest);
        req.montant = m > 0 ? m : rest;
      }
    } else if (cfg.avecFrais) {
      req.montantCapital = Number(v.montantCapital) || 0;
      req.montantFrais = Number(v.montantFrais) || 0;
    } else if (emp) {
      const rest = Math.max(0, Number(emp.montantRestant) || 0);
      const m = Math.min(Number(v.montant) || rest, rest);
      req.montant = m > 0 ? m : rest;
    } else {
      req.montant = v.montant;
    }
    return req;
  }

  private referencePaiementPourRequete(): string | undefined {
    const ref = (this.form.getRawValue().referencePaiement ?? '').trim();
    if (!ref || !modePaiementMobile(this.form.getRawValue().modePaiement)) {
      return undefined;
    }
    return ref;
  }

  selectModePaiement(mode: ModePaiement): void {
    this.form.patchValue({ modePaiement: mode });
    this.onModePaiementChange();
  }

  onModePaiementChange(): void {
    if (!modePaiementMobile(this.form.controls.modePaiement.value)) {
      this.form.controls.referencePaiement.setValue('');
    }
  }
}
