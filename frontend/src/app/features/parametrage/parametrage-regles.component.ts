import { Component, computed, inject, OnInit, signal, HostListener } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import {
  MouvementRegleDto,
  Periodicite,
  RegleOperationDto,
  RegleOperationService,
  TypeModeCalcul,
  TypeOperation,
} from '../../core/services/regle-operation.service';
import {
  calculerDateDerniereEcheance,
  plageEcheanceDepuisMontantsEmprunt,
  reglePaiementUniquePossible,
  simulerEmpruntDepuisRegle,
} from './regle-emprunt-calcul.util';
import { ConfirmDialogService } from '../../core/services/confirm-dialog.service';
import { NotificationService } from '../../core/services/notification.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import { formatFcfa } from '../../core/utils/currency.util';
import {
  modePartsActif,
  resumePlageDepuisDonneesApi,
  resumePlageDepuisPartsSaisies,
  type ResumePlageCotisation,
} from '../../shared/util/parts-cotisation.util';
import { libelleFraisEmprunt } from '../../core/util/regle-emprunt.util';
import { ParametrageTabsComponent } from './parametrage-tabs.component';
import { DROIT_ACTION_IMPORTS } from '../../shared/imports/droit-action.imports';
import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import {
  clampPage,
  paginateSlice,
  paginationTotalPages,
} from '../../shared/util/pagination.util';

export const COMPTES_MOUVEMENT = [
  'MEMBRE.EPARGNE_HEBDO',
  'MEMBRE.EPARGNE_MOIS',
  'MEMBRE.SOLIDARITE',
  'MEMBRE.DEPENSE',
  'ORGANISATION.CAISSE',
  'ORGANISATION.SOLIDARITE',
  'ORGANISATION.BANQUE',
] as const;

export const TYPES_MONTANT = ['MONTANT_SAISI', 'MONTANT_FIXE', 'POURCENTAGE'] as const;

const ICONS: Partial<Record<TypeOperation, string>> = {
  COTISATION: '💰',
  COTISATION_MOIS: '📅',
  VERSEMENT: '📥',
  EMPRUNT: '📈',
  PENALITE: '⚠',
  AMENDE: '🚫',
  DEPENSE: '📤',
  BANQUE_VERSEMENT: '🏛',
  BANQUE_RETRAIT: '🏛',
  REMBOURSEMENT: '↩',
};

const ICON_BG: Partial<Record<TypeOperation, string>> = {
  COTISATION: 'var(--g3)',
  COTISATION_MOIS: 'var(--pi2)',
  VERSEMENT: 'var(--bl2)',
  EMPRUNT: 'var(--g3)',
  PENALITE: 'var(--re2)',
  AMENDE: 'var(--pu2)',
  DEPENSE: '#f1f0eb',
  BANQUE_VERSEMENT: 'var(--bl2)',
};

@Component({
  selector: 'app-parametrage-regles',
  standalone: true,
  imports: [ReactiveFormsModule, ParametrageTabsComponent, ListPaginationComponent, ...DROIT_ACTION_IMPORTS],
  templateUrl: './parametrage-regles.component.html',
  styleUrls: ['./parametrage-regles.component.scss', '../../shared/styles/pagination.scss'],
})
export class ParametrageReglesComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly regleService = inject(RegleOperationService);
  private readonly notify = inject(NotificationService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  readonly formatFcfa = formatFcfa;
  readonly modePartsActif = modePartsActif;
  readonly comptesMouvement = COMPTES_MOUVEMENT;
  readonly typesMontant = TYPES_MONTANT;
  readonly simMontant = 5000;
  readonly simMontantEmprunt = 1000;
  readonly simDateOctroi = signal(this.todayIso());
  readonly modesCalcul: { value: TypeModeCalcul; label: string }[] = [
    { value: 'FIXE', label: 'Montant fixe (FCFA)' },
    { value: 'POURCENTAGE', label: 'Pourcentage (%)' },
  ];

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly regles = signal<RegleOperationDto[]>([]);
  readonly reglesPage = signal(1);
  readonly reglesPageSize = 8;
  readonly reglesPaged = computed(() =>
    paginateSlice(this.regles(), this.reglesPage(), this.reglesPageSize)
  );
  readonly selectedId = signal<number | null>(null);
  readonly selectedRegle = computed(() => {
    const id = this.selectedId();
    return this.regles().find((r) => r.id === id) ?? null;
  });

  readonly periodiciteOptions: { value: Periodicite | ''; label: string }[] = [
    { value: 'HEBDOMADAIRE', label: 'Hebdomadaire' },
    { value: 'MENSUEL', label: 'Mensuelle' },
    { value: 'LIBRE', label: 'Libre' },
  ];

  readonly form = this.fb.group({
    libelle: ['', [Validators.required, Validators.maxLength(255)]],
    periodicite: ['' as Periodicite | ''],
    montantMin: [null as number | null],
    montantMax: [null as number | null],
    montantParPart: [null as number | null],
    partsMin: [null as number | null],
    partsMax: [null as number | null],
    solidariteAuto: [false],
    montantSolidariteAuto: [null as number | null],
    montantAmendeMin: [null as number | null],
    montantAmendeMax: [null as number | null],
    actif: [true],
    typeFrais: ['' as TypeModeCalcul | ''],
    montantFrais: [null as number | null],
    pourcentageFrais: [null as number | null],
    nbEcheancesMin: [null as number | null],
    nbEcheancesMax: [null as number | null],
    nbEcheancesDefaut: [null as number | null],
    jourEcheanceMois: [null as number | null],
    joursAlerteEcheanceProche: [7, [Validators.min(0), Validators.max(90)]],
    montantEcheanceMin: [null as number | null],
    montantEcheanceMax: [null as number | null],
    typePenalite: ['' as TypeModeCalcul | ''],
    montantPenalite: [null as number | null],
    pourcentagePenalite: [null as number | null],
    mouvements: this.fb.array([]),
  });

  get mouvements(): FormArray {
    return this.form.get('mouvements') as FormArray;
  }

  readonly supportsPeriodicite = computed(() => {
    const t = this.selectedRegle()?.typeOperation;
    return t === 'COTISATION' || t === 'COTISATION_MOIS' || t === 'VERSEMENT';
  });

  readonly supportsMontants = computed(() => this.selectedRegle()?.typeOperation === 'EMPRUNT');

  readonly supportsPartsCotisation = computed(() => {
    const t = this.selectedRegle()?.typeOperation;
    return t === 'COTISATION' || t === 'COTISATION_MOIS';
  });

  readonly supportsSolidarite = computed(() => {
    const t = this.selectedRegle()?.typeOperation;
    return t === 'COTISATION' || t === 'COTISATION_MOIS';
  });

  readonly supportsAmendeCotisation = computed(() => this.supportsSolidarite());

  readonly supportsEmprunt = computed(() => this.selectedRegle()?.typeOperation === 'EMPRUNT');

  readonly paiementUniquePossible = computed(() => reglePaiementUniquePossible(this.selectedRegle()));

  simulationEmpruntActuelle(): ReturnType<typeof simulerEmpruntDepuisRegle> | null {
    const regle = this.regleEmpruntDepuisFormulaire();
    if (!regle) return null;
    const v = this.form.getRawValue();
    const nb = v.nbEcheancesDefaut ?? regle.nbEcheancesDefaut ?? undefined;
    return simulerEmpruntDepuisRegle(regle, this.simMontantEmprunt, nb, this.simDateOctroi());
  }

  formatDateFr(iso: string): string {
    if (!iso) return '—';
    const d = new Date(iso + 'T12:00:00');
    if (Number.isNaN(d.getTime())) return '—';
    return new Intl.DateTimeFormat('fr-FR', {
      day: '2-digit',
      month: 'long',
      year: 'numeric',
    }).format(d);
  }

  private todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }

  readonly simulationLignes = computed(() => {
    if (!this.form.valid) return [];
    const montant = this.simMontant;
    const lignes: { label: string; montant: number; cls: string }[] = [];
    let totalCredit = 0;
    let cotisationComptee = false;
    const raw = this.mouvements.getRawValue() as MouvementRegleDto[];
    const t = this.selectedRegle()?.typeOperation;

    for (const m of raw) {
      let mv = 0;
      if (m.typeMontant === 'MONTANT_FIXE') {
        mv = this.form.get('montantSolidariteAuto')?.value ?? 200;
      } else if (m.typeMontant === 'POURCENTAGE') {
        mv = Math.round(montant * 0.01);
      } else {
        mv = montant;
      }
      if (m.typeMontant === 'MONTANT_SAISI' && cotisationComptee) {
        continue;
      }
      if (m.typeMontant === 'MONTANT_SAISI') {
        cotisationComptee = true;
        const nom = t === 'COTISATION_MOIS' ? 'épargne mois' : 'épargne hebdo';
        lignes.push({
          label: `Cotisation — ${nom} + Caisse organisation`,
          montant: mv,
          cls: 'cr-c',
        });
        totalCredit += mv;
        continue;
      }
      const label = `${this.libelleCompte(m.sourceType)} → ${this.libelleCompte(m.cibleType)}`;
      lignes.push({ label, montant: mv, cls: m.typeMontant === 'MONTANT_FIXE' ? 'or-c' : 'cr-c' });
      totalCredit += mv;
    }
    if (this.form.get('solidariteAuto')?.value && !raw.some((m) => m.typeMontant === 'MONTANT_FIXE')) {
      const sol = this.form.get('montantSolidariteAuto')?.value ?? 0;
      if (sol > 0) {
        lignes.push({ label: '🤝 Solidarité auto', montant: sol, cls: 'or-c' });
        totalCredit += sol;
      }
    }
    lignes.push({ label: 'Total crédit', montant: totalCredit, cls: 'cr-c' });
    return lignes;
  });

  ngOnInit(): void {
    this.form.get('typeFrais')?.valueChanges.subscribe((type) => {
      if (!type) {
        this.form.patchValue({ montantFrais: null, pourcentageFrais: null }, { emitEvent: false });
      }
    });
    this.load();
  }

  orgId(): number | null {
    return organisationCouranteId(this.route, this.auth);
  }

  iconFor(r: RegleOperationDto): string {
    if (r.typeOperation === 'EMPRUNT') {
      if (r.libelle.toLowerCase().includes('caisse')) return '🏦';
      if (r.libelle.toLowerCase().includes('solidar')) return '🤝';
    }
    return ICONS[r.typeOperation] ?? '⚙';
  }

  iconBgFor(r: RegleOperationDto): string {
    if (r.typeOperation === 'EMPRUNT' && r.libelle.toLowerCase().includes('caisse')) return 'var(--or3)';
    return ICON_BG[r.typeOperation] ?? 'var(--g3)';
  }

  showResumeParams(r: RegleOperationDto): boolean {
    return (
      r.montantMin != null ||
      r.montantMax != null ||
      r.montantParPart != null ||
      r.partsMin != null ||
      r.partsMax != null ||
      (r.solidariteAuto && r.montantSolidariteAuto != null) ||
      r.montantAmendeMin != null ||
      r.montantAmendeMax != null ||
      r.nbEcheancesDefaut != null ||
      r.jourEcheanceMois != null ||
      r.typeFrais != null ||
      r.typePenalite != null ||
      r.montantEcheanceMin != null ||
      r.montantEcheanceMax != null ||
      r.mouvements.length > 0
    );
  }

  fraisLibelle(r: RegleOperationDto): string {
    return libelleFraisEmprunt(r);
  }

  derniereEcheanceLibelle(r: RegleOperationDto): string {
    const nb = r.nbEcheancesDefaut ?? r.nbEcheancesMax ?? r.nbEcheancesMin ?? 1;
    const date = calculerDateDerniereEcheance(this.simDateOctroi(), nb, r.jourEcheanceMois);
    const jour = r.jourEcheanceMois != null ? ` · jour ${r.jourEcheanceMois}` : '';
    return `${this.formatDateFr(date)}${jour}`;
  }

  echeanceLibelle(r: RegleOperationDto): string {
    if (r.montantEcheanceMin == null && r.montantEcheanceMax == null) {
      return '—';
    }
    const min = r.montantEcheanceMin != null ? this.formatFcfa(r.montantEcheanceMin) : '—';
    const max = r.montantEcheanceMax != null ? this.formatFcfa(r.montantEcheanceMax) : '—';
    const nbMin = r.nbEcheancesMin ?? 1;
    const nbMax = r.nbEcheancesMax ?? nbMin;
    const paiementUniqueSeul = nbMin === 1 && nbMax === 1;
    if (paiementUniqueSeul && min === max) {
      return `${min} (paiement unique)`;
    }
    if (nbMin <= 1) {
      return `${min} – ${max} (si 1 éch. : nominal + frais)`;
    }
    return `${min} – ${max} / échéance`;
  }

  plageEcheancePourAppliquer(): { min: number; max: number } | null {
    if (!this.supportsEmprunt() || !this.paiementUniquePossible()) {
      return null;
    }
    const v = this.form.getRawValue();
    return plageEcheanceDepuisMontantsEmprunt(
      this.regleEmpruntDepuisFormulaire(),
      v.montantMin,
      v.montantMax
    );
  }

  appliquerMontantEcheanceDepuisPlageEmprunt(): void {
    const plage = this.plageEcheancePourAppliquer();
    if (!plage) {
      this.showToast('Renseignez le montant minimum et maximum emprunt.');
      return;
    }
    this.form.patchValue({
      montantEcheanceMin: plage.min,
      montantEcheanceMax: plage.max,
    });
    this.showToast('Montants échéance alignés sur la plage min/max emprunt (+ frais).');
  }

  private regleEmpruntDepuisFormulaire(): RegleOperationDto | null {
    const base = this.selectedRegle();
    if (!base || base.typeOperation !== 'EMPRUNT') {
      return null;
    }
    const v = this.form.getRawValue();
    return {
      ...base,
      typeFrais: (v.typeFrais || null) as TypeModeCalcul | null,
      montantFrais: v.typeFrais === 'FIXE' ? v.montantFrais : null,
      pourcentageFrais: v.typeFrais === 'POURCENTAGE' ? v.pourcentageFrais : null,
      nbEcheancesMin: v.nbEcheancesMin,
      nbEcheancesMax: v.nbEcheancesMax,
      nbEcheancesDefaut: v.nbEcheancesDefaut,
      jourEcheanceMois: v.jourEcheanceMois,
      joursAlerteEcheanceProche: v.joursAlerteEcheanceProche,
      montantEcheanceMin: v.montantEcheanceMin,
      montantEcheanceMax: v.montantEcheanceMax,
      typePenalite: (v.typePenalite || null) as TypeModeCalcul | null,
      montantPenalite: v.montantPenalite,
      pourcentagePenalite: v.pourcentagePenalite,
    };
  }

  penaliteLibelle(r: RegleOperationDto): string {
    if (r.typePenalite === 'POURCENTAGE' && r.pourcentagePenalite != null) {
      return `${r.pourcentagePenalite} %`;
    }
    if (r.typePenalite === 'FIXE' && r.montantPenalite != null) {
      return this.formatFcfa(r.montantPenalite);
    }
    return '—';
  }

  typeLabel(r: RegleOperationDto): string {
    const p = r.periodicite ? ` · ${this.periodiciteLabel(r.periodicite)}` : '';
    if (r.typeOperation === 'EMPRUNT') return `EMPRUNT · ${r.libelle}`;
    return `${r.typeOperation}${p}`;
  }

  periodiciteLabel(p: Periodicite): string {
    const map: Record<Periodicite, string> = {
      HEBDOMADAIRE: 'Hebdomadaire',
      MENSUEL: 'Mensuelle',
      LIBRE: 'Libre',
    };
    return map[p] ?? p;
  }

  libelleCompte(code: string): string {
    return code.replace('MEMBRE.', 'Membre · ').replace('ORGANISATION.', 'Organisation · ');
  }

  labelTypeMontant(t: string): string {
    if (t === 'MONTANT_SAISI') return 'MONTANT TOTAL';
    if (t === 'MONTANT_FIXE') return 'MONTANT FIXE';
    return t;
  }

  /** Recalculé à chaque cycle de détection (suit les champs parts en direct). */
  resumePlageFormulaire(): ResumePlageCotisation | null {
    if (!this.supportsPartsCotisation()) return null;
    return resumePlageDepuisPartsSaisies({
      montantParPart: this.form.get('montantParPart')?.value,
      partsMin: this.form.get('partsMin')?.value,
      partsMax: this.form.get('partsMax')?.value,
    });
  }

  resumeRegleCotisationCarte(r: RegleOperationDto): ResumePlageCotisation | null {
    if (!modePartsActif(r)) return null;
    return resumePlageDepuisDonneesApi({
      montantParPart: r.montantParPart,
      partsMin: r.partsMin,
      partsMax: r.partsMax,
      montantMin: r.montantMin,
      montantMax: r.montantMax,
    });
  }

  goReglesPage(p: number): void {
    this.reglesPage.set(clampPage(p, paginationTotalPages(this.regles().length, this.reglesPageSize)));
  }

  selectRegle(r: RegleOperationDto): void {
    this.selectedId.set(r.id);
    this.patchForm(r);
  }

  toggleActif(r: RegleOperationDto, event: Event): void {
    event.stopPropagation();
    const orgId = this.orgId();
    if (!orgId) return;
    const actif = !r.actif;
    this.regleService.basculerActif(orgId, r.id, actif).subscribe({
      next: (updated) => {
        this.regles.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
        if (this.selectedId() === updated.id) {
          this.form.patchValue({ actif: updated.actif });
        }
        this.showToast(actif ? 'Règle activée' : 'Règle désactivée');
      },
      error: () => this.showToast('Erreur lors du changement de statut'),
    });
  }

  toggleSolidarite(): void {
    const on = this.form.get('solidariteAuto')?.value;
    if (!on) {
      this.form.get('montantSolidariteAuto')?.disable();
    } else {
      this.form.get('montantSolidariteAuto')?.enable();
    }
  }

  ajouterMouvement(): void {
    const ordre = this.mouvements.length + 1;
    const epargne =
      this.selectedRegle()?.typeOperation === 'COTISATION_MOIS'
        ? 'MEMBRE.EPARGNE_MOIS'
        : 'MEMBRE.EPARGNE_HEBDO';
    this.mouvements.push(
      this.fb.group({
        ordre: [ordre],
        sourceType: [epargne, Validators.required],
        cibleType: ['ORGANISATION.CAISSE', Validators.required],
        sens: ['CREDIT' as const, Validators.required],
        typeMontant: ['MONTANT_SAISI', Validators.required],
      })
    );
    this.showToast('Nouveau mouvement ajouté');
  }

  supprimerMouvement(i: number): void {
    this.mouvements.removeAt(i);
    this.reordonnerMouvements();
    this.showToast('Mouvement supprimé');
  }

  enregistrer(): void {
    const orgId = this.orgId();
    const regle = this.selectedRegle();
    if (!orgId || !regle || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    let montantMin = v.montantMin;
    let montantMax = v.montantMax;
    let montantParPart = v.montantParPart;
    let partsMin = v.partsMin;
    let partsMax = v.partsMax;
    if (this.supportsPartsCotisation()) {
      const resume = resumePlageDepuisPartsSaisies({
        montantParPart: v.montantParPart,
        partsMin: v.partsMin,
        partsMax: v.partsMax,
      });
      if (!resume) {
        this.notify.error('Renseignez la valeur d\'une part et les nombres de parts min/max.');
        this.saving.set(false);
        return;
      }
      if (resume.partsMin > resume.partsMax) {
        this.notify.error('Le nombre minimum de parts ne peut pas dépasser le maximum.');
        this.saving.set(false);
        return;
      }
      montantParPart = resume.montantParPart;
      partsMin = resume.partsMin;
      partsMax = resume.partsMax;
      montantMin = resume.montantMin;
      montantMax = resume.montantMax;
    }
    const body = {
      libelle: v.libelle!,
      periodicite: v.periodicite || null,
      montantMin,
      montantMax,
      montantParPart,
      partsMin,
      partsMax,
      solidariteAuto: !!v.solidariteAuto,
      montantSolidariteAuto: v.montantSolidariteAuto,
      montantAmendeMin: v.montantAmendeMin,
      montantAmendeMax: v.montantAmendeMax,
      typeFrais: v.typeFrais || null,
      montantFrais: v.typeFrais === 'FIXE' ? v.montantFrais : null,
      pourcentageFrais: v.typeFrais === 'POURCENTAGE' ? v.pourcentageFrais : null,
      nbEcheancesMin: v.nbEcheancesMin,
      nbEcheancesMax: v.nbEcheancesMax,
      nbEcheancesDefaut: v.nbEcheancesDefaut,
      jourEcheanceMois: v.jourEcheanceMois,
      joursAlerteEcheanceProche: v.joursAlerteEcheanceProche,
      montantEcheanceMin: v.montantEcheanceMin,
      montantEcheanceMax: v.montantEcheanceMax,
      typePenalite: v.typePenalite || null,
      montantPenalite: v.montantPenalite,
      pourcentagePenalite: v.pourcentagePenalite,
      actif: !!v.actif,
      mouvements: (v.mouvements as MouvementRegleDto[]).map((m, i) => ({
        ...m,
        ordre: i + 1,
      })),
    };
    this.regleService.mettreAJour(orgId, regle.id, body).subscribe({
      next: (updated) => {
        this.regles.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
        this.patchForm(updated);
        this.saving.set(false);
        this.showToast('Règle enregistrée avec succès !');
      },
      error: () => {
        this.saving.set(false);
        this.showToast('Erreur lors de l\'enregistrement');
      },
    });
  }

  annuler(): void {
    const r = this.selectedRegle();
    if (r) this.patchForm(r);
  }

  reinitialiser(): void {
    const orgId = this.orgId();
    if (!orgId) {
      return;
    }
    void this.confirmDialog
      .confirm({
        title: 'Réinitialiser les règles',
        message:
          'Réinitialiser toutes les règles aux valeurs par défaut ? Les paramètres actuels seront remplacés.',
        confirmLabel: 'Réinitialiser',
        variant: 'danger',
      })
      .then((ok) => {
        if (!ok) {
          return;
        }
        this.loading.set(true);
        this.regleService.reinitialiser(orgId).subscribe({
          next: (list) => {
            this.regles.set(list);
            this.reglesPage.set(1);
            if (list.length) {
              this.selectRegle(list[0]);
            }
            this.loading.set(false);
            this.showToast('Règles réinitialisées aux valeurs par défaut');
          },
          error: () => {
            this.loading.set(false);
            this.showToast('Erreur lors de la réinitialisation');
          },
        });
      });
  }

  simuler(): void {
    this.showToast('Simulation mise à jour');
  }

  readonly reglesCotisation = computed(() =>
    this.regles().filter(
      (r) => r.typeOperation === 'COTISATION' || r.typeOperation === 'COTISATION_MOIS'
    )
  );

  private load(): void {
    const orgId = this.orgId();
    if (!orgId) return;
    this.loading.set(true);
    this.regleService.lister(orgId).subscribe({
      next: (list) => {
        this.regles.set(list);
        this.reglesPage.set(1);
        if (list.length && !this.selectedId()) {
          const hebdo =
            list.find((r) => r.typeOperation === 'COTISATION') ??
            list.find((r) => r.typeOperation === 'COTISATION_MOIS') ??
            list[0];
          this.selectRegle(hebdo);
        }
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.showToast('Impossible de charger les règles');
      },
    });
  }

  private patchForm(r: RegleOperationDto): void {
    this.mouvements.clear();
    for (const m of r.mouvements) {
      this.mouvements.push(
        this.fb.group({
          ordre: [m.ordre],
          sourceType: [m.sourceType, Validators.required],
          cibleType: [m.cibleType, Validators.required],
          sens: [m.sens, Validators.required],
          typeMontant: [m.typeMontant, Validators.required],
        })
      );
    }
    const cotisParts =
      (r.typeOperation === 'COTISATION' || r.typeOperation === 'COTISATION_MOIS') &&
      resumePlageDepuisDonneesApi({
        montantParPart: r.montantParPart,
        partsMin: r.partsMin,
        partsMax: r.partsMax,
        montantMin: r.montantMin,
        montantMax: r.montantMax,
      });
    this.form.patchValue({
      libelle: r.libelle,
      periodicite: r.periodicite ?? '',
      montantMin: cotisParts ? cotisParts.montantMin : r.montantMin,
      montantMax: cotisParts ? cotisParts.montantMax : r.montantMax,
      montantParPart: cotisParts ? cotisParts.montantParPart : (r.montantParPart ?? null),
      partsMin: cotisParts ? cotisParts.partsMin : (r.partsMin ?? null),
      partsMax: cotisParts ? cotisParts.partsMax : (r.partsMax ?? null),
      solidariteAuto: r.solidariteAuto,
      montantSolidariteAuto: r.montantSolidariteAuto,
      montantAmendeMin: r.montantAmendeMin,
      montantAmendeMax: r.montantAmendeMax,
      typeFrais: r.typeFrais ?? '',
      montantFrais: r.montantFrais,
      pourcentageFrais: r.pourcentageFrais,
      nbEcheancesMin: r.nbEcheancesMin,
      nbEcheancesMax: r.nbEcheancesMax,
      nbEcheancesDefaut: r.nbEcheancesDefaut,
      jourEcheanceMois: r.jourEcheanceMois,
      joursAlerteEcheanceProche: r.joursAlerteEcheanceProche ?? 7,
      montantEcheanceMin: r.montantEcheanceMin,
      montantEcheanceMax: r.montantEcheanceMax,
      typePenalite: r.typePenalite ?? '',
      montantPenalite: r.montantPenalite,
      pourcentagePenalite: r.pourcentagePenalite,
      actif: r.actif,
    });
    if (r.solidariteAuto) {
      this.form.get('montantSolidariteAuto')?.enable();
    } else {
      this.form.get('montantSolidariteAuto')?.disable();
    }
  }

  private reordonnerMouvements(): void {
    this.mouvements.controls.forEach((c, i) => c.patchValue({ ordre: i + 1 }));
  }

  private showToast(msg: string): void {
    this.notify.show(msg);
  }
}
