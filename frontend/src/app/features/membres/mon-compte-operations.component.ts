import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { NotificationService } from '../../core/services/notification.service';
import {
  DemandeOperationMembreResponse,
  MonCompteOperationService,
} from '../../core/services/mon-compte-operation.service';
import {
  EmpruntDto,
  EmpruntService,
  RembourserRequest,
} from '../../core/services/emprunt.service';
import { RegleOperationService } from '../../core/services/regle-operation.service';
import {
  REGLE_HEBDO_FALLBACK,
  REGLE_MOIS_FALLBACK,
  regleCotisationDepuisDto,
  RegleCotisationUi,
} from '../../core/util/regle-cotisation.util';
import { formatFcfa } from '../../core/utils/currency.util';
import {
  modePartsActif,
  montantDepuisParts,
  partDefaut,
} from '../../shared/util/parts-cotisation.util';
import { MODES_PAIEMENT, type ModePaiement } from '../../shared/util/mode-paiement.util';
import { buildSemaineOptions, semaineCouranteKey } from '../operations/cotisation-mois/cotisation-semaine.util';
import { buildMoisOptions, moisCourantKey } from '../operations/cotisation-mois/cotisation-mois.util';
import {
  echeancePrioritairePourRemboursement,
  echeanceRestant,
  typeEmpruntLabel,
} from '../remboursements/remboursement-emprunt.util';

export type MonComptePanel = 'cotis' | 'remb';

@Component({
  selector: 'app-mon-compte-operations',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './mon-compte-operations.component.html',
  styleUrls: ['./mon-compte-operations.component.scss', '../../shared/styles/mode-paiement.scss'],
})
export class MonCompteOperationsComponent implements OnInit {
  readonly orgId = input.required<number>();
  readonly membreId = input.required<number>();
  readonly empruntsInit = input<EmpruntDto[]>([]);
  private readonly fb = inject(FormBuilder);
  private readonly monCompteOp = inject(MonCompteOperationService);
  private readonly empruntService = inject(EmpruntService);
  private readonly regleService = inject(RegleOperationService);
  private readonly notify = inject(NotificationService);

  readonly formatFcfa = formatFcfa;
  /** Mon compte : mobile money uniquement (pas d'espèces). */
  readonly modesPaiement = MODES_PAIEMENT.filter((m) => m.value !== 'ESPECES');
  readonly typeEmpruntLabel = typeEmpruntLabel;

  readonly sectionOuverte = signal(false);
  readonly panel = signal<MonComptePanel>('cotis');
  readonly cotisType = signal<'hebdo' | 'mois'>('hebdo');
  readonly loading = signal(false);
  readonly regleHebdo = signal<RegleCotisationUi>(REGLE_HEBDO_FALLBACK);
  readonly regleMois = signal<RegleCotisationUi>(REGLE_MOIS_FALLBACK);
  readonly emprunts = signal<EmpruntDto[]>([]);
  readonly mesDemandes = signal<DemandeOperationMembreResponse[]>([]);

  readonly demandesEnAttente = computed(() =>
    this.mesDemandes().filter((d) => d.statut === 'EN_ATTENTE')
  );

  readonly demandesRejetees = computed(() =>
    this.mesDemandes().filter((d) => d.statut === 'REFUSEE')
  );
  readonly semaineOptions = buildSemaineOptions();
  readonly moisOptions = buildMoisOptions();

  readonly regleActive = computed(() =>
    this.cotisType() === 'hebdo' ? this.regleHebdo() : this.regleMois()
  );

  readonly utiliseParts = computed(() => modePartsActif(this.regleActive()));

  readonly plageParts = computed(() => {
    const r = this.regleActive();
    if (!modePartsActif(r) || r.partsMin == null || r.partsMax == null) return null;
    return { min: r.partsMin, max: r.partsMax };
  });

  readonly optionsPartsListe = computed(() => {
    const plage = this.plageParts();
    if (!plage) return [];
    const out: number[] = [];
    for (let n = plage.min; n <= plage.max; n++) out.push(n);
    return out;
  });

  readonly empruntsEnCours = computed(() =>
    this.emprunts().filter((e) => e.statut === 'EN_COURS' && e.montantRestant > 0)
  );

  readonly empruntSelectionne = computed(() => {
    const id = this.formRemb.controls.empruntId.value;
    if (id == null) return null;
    return this.empruntsEnCours().find((e) => e.id === id) ?? null;
  });

  readonly echeanceRemb = computed(() => {
    const emp = this.empruntSelectionne();
    if (!emp) return null;
    return echeancePrioritairePourRemboursement(emp);
  });

  readonly formCotis = this.fb.nonNullable.group({
    nbParts: [1, Validators.required],
    montant: [5000, [Validators.required, Validators.min(1)]],
    semaineKey: [semaineCouranteKey()],
    moisAnnee: [moisCourantKey(), Validators.required],
    dateOperation: [this.todayIso(), Validators.required],
    modePaiement: ['WAVE' as ModePaiement],
    referencePaiement: [''],
    observation: [''],
  });

  readonly formRemb = this.fb.nonNullable.group({
    empruntId: [null as number | null, Validators.required],
    montant: [0, [Validators.required, Validators.min(1)]],
    echeanceId: [null as number | null],
    datePaiement: [this.todayIso(), Validators.required],
    modePaiement: ['WAVE' as ModePaiement],
    referencePaiement: [''],
    observation: [''],
  });

  ngOnInit(): void {
    this.emprunts.set(this.empruntsInit());
    this.regleService.obtenirCotisations(this.orgId()).subscribe({
      next: (cot) => {
        this.regleHebdo.set(regleCotisationDepuisDto(cot.hebdomadaire, REGLE_HEBDO_FALLBACK));
        this.regleMois.set(regleCotisationDepuisDto(cot.mensuelle, REGLE_MOIS_FALLBACK));
        this.appliquerRegleCotis();
      },
    });
    if (this.empruntsInit().length === 0) {
      this.empruntService.listerMesEmprunts(this.orgId()).subscribe({
        next: (list) => this.emprunts.set(list),
      });
    }
    this.chargerMesDemandes();
  }

  chargerMesDemandes(): void {
    this.monCompteOp.mesDemandes(this.orgId()).subscribe({
      next: (list) => this.mesDemandes.set(list),
      error: () => this.mesDemandes.set([]),
    });
  }

  toggleSection(): void {
    this.sectionOuverte.update((v) => !v);
  }

  setPanel(p: MonComptePanel): void {
    this.panel.set(p);
  }

  setCotisType(t: 'hebdo' | 'mois'): void {
    this.cotisType.set(t);
    this.appliquerRegleCotis();
  }

  montantDepuisPartsAffiche(): number {
    const r = this.regleActive();
    if (!modePartsActif(r) || !r.montantParPart) {
      return Number(this.formCotis.controls.montant.value) || 0;
    }
    const nb = Number(this.formCotis.controls.nbParts.value) || 0;
    return montantDepuisParts(nb, r.montantParPart);
  }

  onNbPartsChange(): void {
    const r = this.regleActive();
    if (!modePartsActif(r) || !r.montantParPart) return;
    let nb = Math.floor(Number(this.formCotis.controls.nbParts.value) || 0);
    const plage = this.plageParts();
    if (plage) {
      if (nb < plage.min) nb = plage.min;
      if (nb > plage.max) nb = plage.max;
      this.formCotis.controls.nbParts.setValue(nb, { emitEvent: false });
    }
    this.formCotis.patchValue(
      { montant: montantDepuisParts(nb, r.montantParPart) },
      { emitEvent: false }
    );
  }

  selectModeCotis(mode: ModePaiement): void {
    this.formCotis.patchValue({ modePaiement: mode });
  }

  selectModeRemb(mode: ModePaiement): void {
    this.formRemb.patchValue({ modePaiement: mode });
  }

  onEmpruntRembChange(): void {
    const emp = this.empruntSelectionne();
    const ech = this.echeanceRemb();
    if (!emp) return;
    const montant =
      emp.typeEmprunt === 'SOLIDARITE'
        ? emp.montantRestant
        : ech
          ? echeanceRestant(ech)
          : emp.montantRestant;
    this.formRemb.patchValue({
      montant: Math.max(1, Math.round(montant)),
      echeanceId: ech?.id ?? null,
    });
  }

  validerCotisation(): void {
    this.onNbPartsChange();
    const v = this.formCotis.getRawValue();
    if (!this.validerReferenceMobile(v.referencePaiement)) return;

    const body = {
      membreId: this.membreId(),
      montant: v.montant,
      dateOperation: v.dateOperation,
      observation: v.observation || undefined,
      modePaiement: v.modePaiement,
      referencePaiement: this.refPourRequete(v.referencePaiement),
    };

    this.loading.set(true);
    const orgId = this.orgId();
    const call =
      this.cotisType() === 'hebdo'
        ? this.monCompteOp.validerCotisationHebdo(orgId, {
            ...body,
            semaineKey: v.semaineKey,
          })
        : this.monCompteOp.validerCotisationMois(orgId, {
            ...body,
            moisAnnee: v.moisAnnee,
          });

    call.subscribe({
      next: (res) => {
        this.loading.set(false);
        this.notify.show(res.message ?? '✅ Demande de cotisation envoyée.');
        this.chargerMesDemandes();
        this.appliquerRegleCotis();
        this.formCotis.patchValue({ referencePaiement: '', observation: '' });
      },
      error: (err) => {
        this.loading.set(false);
        const msg = err?.error?.message;
        this.notify.error(typeof msg === 'string' ? msg : 'Erreur lors de la cotisation.');
      },
    });
  }

  validerRemboursement(): void {
    const v = this.formRemb.getRawValue();
    const emp = this.empruntSelectionne();
    if (!emp || v.empruntId == null) {
      this.notify.show('Sélectionnez un emprunt.');
      return;
    }
    if (!this.validerReferenceMobile(v.referencePaiement)) return;

    const body: RembourserRequest = {
      echeanceId: v.echeanceId ?? undefined,
      montant: v.montant,
      datePaiement: v.datePaiement,
      modePaiement: v.modePaiement,
      referencePaiement: this.refPourRequete(v.referencePaiement),
      observation: v.observation || undefined,
      appliquerPenalite: false,
    };

    this.loading.set(true);
    this.monCompteOp.rembourser(this.orgId(), emp.id, body).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.notify.show(res.message ?? '✅ Demande de remboursement envoyée.');
        this.chargerMesDemandes();
        this.empruntService.listerMesEmprunts(this.orgId()).subscribe({
          next: (list) => {
            this.emprunts.set(list);
            this.formRemb.patchValue({
              empruntId: null,
              montant: 0,
              referencePaiement: '',
              observation: '',
            });
          },
        });
      },
      error: (err) => {
        this.loading.set(false);
        const msg = err?.error?.message;
        this.notify.error(typeof msg === 'string' ? msg : 'Erreur lors du remboursement.');
      },
    });
  }

  private appliquerRegleCotis(): void {
    const r = this.regleActive();
    if (modePartsActif(r) && r.partsMin != null && r.partsMax != null && r.montantParPart) {
      const p = partDefaut(r.partsMin, r.partsMax);
      this.formCotis.patchValue({
        nbParts: p,
        montant: montantDepuisParts(p, r.montantParPart),
      });
    }
  }

  private validerReferenceMobile(ref: string): boolean {
    if (!ref.trim()) {
      this.notify.show('Indiquez le n° de transaction Wave ou Orange Money.');
      return false;
    }
    return true;
  }

  private refPourRequete(ref: string): string {
    return ref.trim();
  }

  private todayIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
