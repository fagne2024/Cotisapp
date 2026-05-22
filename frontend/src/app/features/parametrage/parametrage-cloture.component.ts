import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, OnInit, signal, HostListener } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import {
  MembrePourcentageRepartitionDto,
  ParametrageClotureDto,
  ParametrageClotureService,
  POSTES_PARTAGE_DEFAUT,
  PostePartageClotureDto,
  PreviewClotureExerciceDto,
  TypeOperationCloture,
} from '../../core/services/parametrage-cloture.service';
import { MembreService } from '../../core/services/membre.service';
import { ClotureRepartitionPreviewComponent } from '../cloture/cloture-repartition-preview.component';
import { TypeModeCalcul } from '../../core/services/regle-operation.service';
import { NotificationService } from '../../core/services/notification.service';
import { organisationCouranteId } from '../../core/util/org-route.util';
import { formatFcfa } from '../../core/utils/currency.util';
import { ParametrageTabsComponent } from './parametrage-tabs.component';
import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import { paginationTotalPages } from '../../shared/util/pagination.util';

interface RetenueFormRow {
  libelle: string;
  typeMode: TypeModeCalcul;
  valeur: number;
  ordre: number;
}

@Component({
  selector: 'app-parametrage-cloture',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
    ParametrageTabsComponent,
    ClotureRepartitionPreviewComponent,
    ListPaginationComponent,
  ],
  templateUrl: './parametrage-cloture.component.html',
  styleUrls: [
    './parametrage-cloture.component.scss',
    '../../shared/styles/pagination.scss',
  ],
})
export class ParametrageClotureComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ParametrageClotureService);
  private readonly membreService = inject(MembreService);
  private readonly notify = inject(NotificationService);

  readonly formatFcfa = formatFcfa;
  readonly chargement = signal(true);
  readonly enregistrement = signal(false);
  readonly previewRepartition = signal<PreviewClotureExerciceDto | null>(null);
  readonly previewLoading = signal(false);
  readonly pctPageSize = 10;
  readonly pctPage = signal(1);
  readonly typesOperation: { value: TypeOperationCloture; label: string }[] = [
    { value: 'COTISATION', label: 'Cotisation hebdo' },
    { value: 'COTISATION_MOIS', label: 'Cotisation mois' },
    { value: 'EMPRUNT', label: 'Emprunt' },
    { value: 'REMBOURSEMENT', label: 'Remboursement' },
    { value: 'PENALITE', label: 'Pénalité' },
    { value: 'AMENDE', label: 'Amende' },
    { value: 'DEPENSE', label: 'Dépense' },
    { value: 'DEPOT_BANQUE', label: 'Dépôt banque' },
    { value: 'RETRAIT_BANQUE', label: 'Retrait banque' },
  ];

  readonly form = this.fb.nonNullable.group({
    cotisationMontantMin: [1000, [Validators.required, Validators.min(1)]],
    cotisationMontantMax: [10000, [Validators.required, Validators.min(1)]],
    partsMin: [1, [Validators.required, Validators.min(1)]],
    partsMax: [10, [Validators.required, Validators.min(2)]],
    modeRepartition: ['PRORATA' as 'PRORATA' | 'EQUITABLE'],
    modeAgregationPostes: ['SEPARER' as 'SEPARER' | 'ADDITIONNER' | 'GROUPES'],
    modeCalculProrata: ['PARTS' as 'PARTS' | 'POURCENTAGE'],
    exclureMembresPretEnCours: [false],
    fraisClotureType: ['FIXE' as TypeModeCalcul],
    fraisClotureValeur: [0, [Validators.min(0)]],
    postesPartage: this.fb.array([]),
    pourcentagesRepartition: this.fb.array([]),
    retenues: this.fb.array([]),
  });

  readonly pourcentageIndicesPage = computed(() => {
    const n = this.pourcentages.length;
    const start = (this.pctPage() - 1) * this.pctPageSize;
    const end = Math.min(start + this.pctPageSize, n);
    return Array.from({ length: Math.max(0, end - start) }, (_, i) => start + i);
  });

  constructor() {
    effect(() => {
      const n = this.pourcentages.length;
      const max = paginationTotalPages(n, this.pctPageSize);
      if (this.pctPage() > max) {
        this.pctPage.set(max);
      }
    });
  }

  ngOnInit(): void {
    const orgId = organisationCouranteId(this.route, this.auth) ?? 0;
    if (orgId < 1) {
      this.chargement.set(false);
      return;
    }
    this.api.get(orgId).subscribe({
      next: (p) => {
        this.appliquer(p);
        this.chargement.set(false);
        this.chargerPreviewRepartition();
      },
      error: () => {
        this.chargement.set(false);
        this.notify.error('Impossible de charger le paramétrage de clôture.');
      },
    });
  }

  get postesPartage(): FormArray {
    return this.form.controls.postesPartage;
  }

  get retenues(): FormArray {
    return this.form.controls.retenues;
  }

  get pourcentages(): FormArray {
    return this.form.controls.pourcentagesRepartition;
  }

  posteEstBuiltin(index: number): boolean {
    return this.postesPartage.at(index).get('builtIn')?.value === true;
  }

  ajouterPostePersonnalise(): void {
    const code = `CUSTOM_${Date.now()}`;
    this.postesPartage.push(this.creerPosteGroupe({
      code,
      libelle: 'Autre montant à partager',
      actif: true,
      builtIn: false,
      compteMembre: 'EPARGNE_HEBDO',
      compteSourceOrg: 'CAISSE',
      typeOperation: 'DEPENSE',
      groupePartage: 1,
      inclureDansPoolAdditionne: true,
    }));
  }

  toutAdditionnerAuPool(): void {
    this.postesPartage.controls.forEach((c) => {
      if (c.get('actif')?.value) {
        c.patchValue({ inclureDansPoolAdditionne: true });
      }
    });
  }

  rienAdditionnerAuPool(): void {
    this.postesPartage.controls.forEach((c) => c.patchValue({ inclureDansPoolAdditionne: false }));
  }

  sommePourcentages(): number {
    return this.pourcentages.controls.reduce(
      (s, c) => s + (Number(c.get('pourcentage')?.value) || 0),
      0
    );
  }

  onPctPageChange(p: number): void {
    this.pctPage.set(p);
  }

  repartirPourcentagesEquitablement(): void {
    const n = this.pourcentages.length;
    if (n === 0) return;
    const base = Math.floor((100 / n) * 100) / 100;
    let reste = 100;
    this.pourcentages.controls.forEach((c, i) => {
      const v = i === n - 1 ? reste : base;
      c.patchValue({ pourcentage: v });
      reste = Math.round((reste - v) * 100) / 100;
    });
  }

  chargerPourcentagesMembres(orgId: number): void {
    this.membreService.lister(orgId, true).subscribe({
      next: (membres) => {
        this.pctPage.set(1);
        this.pourcentages.clear();
        const actifs = membres.filter((m) => m.actif);
        const n = actifs.length || 1;
        const pct = Math.round((100 / n) * 100) / 100;
        let reste = 100;
        actifs.forEach((m, i) => {
          const v = i === actifs.length - 1 ? reste : pct;
          this.pourcentages.push(
            this.fb.nonNullable.group({
              membreId: [m.id],
              codeMembre: [m.codeMembre],
              nomComplet: [m.nomComplet],
              pourcentage: [v, [Validators.required, Validators.min(0.01), Validators.max(100)]],
            })
          );
          reste = Math.round((reste - v) * 100) / 100;
        });
      },
    });
  }

  retirerPoste(index: number): void {
    if (this.posteEstBuiltin(index)) return;
    this.postesPartage.removeAt(index);
  }

  ajouterRetenue(): void {
    this.retenues.push(
      this.fb.nonNullable.group({
        libelle: ['', Validators.required],
        typeMode: ['FIXE' as TypeModeCalcul],
        valeur: [0, [Validators.min(0)]],
        ordre: [this.retenues.length + 1],
      })
    );
  }

  retirerRetenue(i: number): void {
    this.retenues.removeAt(i);
  }

  enregistrer(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const orgId = organisationCouranteId(this.route, this.auth) ?? 0;
    if (orgId < 1) return;
    const raw = this.form.getRawValue();
    const postes = raw.postesPartage as PostePartageClotureDto[];
    if (!postes.some((p) => p.actif)) {
      this.notify.error('Activez au moins un montant à partager.');
      return;
    }
    if (
      raw.modeRepartition === 'PRORATA' &&
      raw.modeCalculProrata === 'POURCENTAGE' &&
      Math.abs(this.sommePourcentages() - 100) > 0.01
    ) {
      this.notify.error('La somme des pourcentages doit être égale à 100 %.');
      return;
    }
    const body: ParametrageClotureDto = {
      organisationId: orgId,
      cotisationMontantMin: Number(raw.cotisationMontantMin),
      cotisationMontantMax: Number(raw.cotisationMontantMax),
      partsMin: Number(raw.partsMin),
      partsMax: Number(raw.partsMax),
      partagerInterets: postes.find((p) => p.code === 'INTERETS')?.actif ?? false,
      partagerPenalites: postes.find((p) => p.code === 'PENALITES')?.actif ?? false,
      partagerAmendes: postes.find((p) => p.code === 'AMENDES')?.actif ?? false,
      modeRepartition: raw.modeRepartition,
      modeAgregationPostes: raw.modeAgregationPostes,
      modeCalculProrata: raw.modeCalculProrata,
      exclureMembresPretEnCours: raw.exclureMembresPretEnCours,
      pourcentagesRepartition:
        raw.modeRepartition === 'PRORATA' && raw.modeCalculProrata === 'POURCENTAGE'
          ? (raw.pourcentagesRepartition as MembrePourcentageRepartitionDto[]).map((x) => ({
              membreId: x.membreId,
              pourcentage: Number(x.pourcentage),
            }))
          : undefined,
      postesPartage: postes.map((p) => ({
        ...p,
        code: p.code.trim().toUpperCase(),
        libelle: p.libelle.trim(),
        groupePartage:
          raw.modeAgregationPostes === 'GROUPES' ? Number(p.groupePartage) || 1 : undefined,
        inclureDansPoolAdditionne:
          raw.modeAgregationPostes === 'ADDITIONNER' ? !!p.inclureDansPoolAdditionne : false,
      })),
      fraisClotureType: raw.fraisClotureType,
      fraisClotureValeur: Number(raw.fraisClotureValeur),
      retenues: (raw.retenues as RetenueFormRow[]).map((r, i) => ({
        libelle: r.libelle,
        typeMode: r.typeMode,
        valeur: Number(r.valeur),
        ordre: r.ordre > 0 ? r.ordre : i + 1,
      })),
      compteVersementMembre: 'EPARGNE_HEBDO',
      compteSourceOrg: 'CAISSE',
    };
    this.enregistrement.set(true);
    this.api.enregistrer(orgId, body).subscribe({
      next: () => {
        this.enregistrement.set(false);
        this.notify.success('Paramétrage de clôture enregistré.');
        this.chargerPreviewRepartition();
      },
      error: (err) => {
        this.enregistrement.set(false);
        this.notify.error(err?.error?.message ?? 'Enregistrement impossible.');
      },
    });
  }

  chargerPreviewRepartition(): void {
    const orgId = organisationCouranteId(this.route, this.auth) ?? 0;
    if (orgId < 1) return;
    this.previewLoading.set(true);
    this.api.previewRepartition(orgId).subscribe({
      next: (p) => {
        this.previewRepartition.set(p);
        this.previewLoading.set(false);
      },
      error: (err) => {
        this.previewRepartition.set(null);
        this.previewLoading.set(false);
        this.notify.error(err?.error?.message ?? 'Prévisualisation impossible (exercice courant requis).');
      },
    });
  }

  orgCourante(): number | null {
    return organisationCouranteId(this.route, this.auth);
  }

  libelleCompte(type: string): string {
    const map: Record<string, string> = {
      INTERET: 'Intérêts',
      PENALITE: 'Pénalité',
      AMENDE: 'Amende',
      EPARGNE_HEBDO: 'Épargne hebdo',
      EPARGNE_MOIS: 'Épargne mois',
      SOLIDARITE: 'Solidarité',
      CAISSE: 'Caisse',
      BANQUE: 'Banque',
    };
    return map[type] ?? type;
  }

  private creerPosteGroupe(p: PostePartageClotureDto) {
    return this.fb.nonNullable.group({
      code: [p.code],
      libelle: [p.libelle, Validators.required],
      actif: [p.actif],
      builtIn: [p.builtIn],
      compteMembre: [p.compteMembre],
      compteSourceOrg: [p.compteSourceOrg],
      typeOperation: [p.typeOperation ?? null],
      groupePartage: [p.groupePartage ?? 1],
      inclureDansPoolAdditionne: [p.inclureDansPoolAdditionne ?? false],
    });
  }

  private appliquer(p: ParametrageClotureDto): void {
    this.pctPage.set(1);
    this.postesPartage.clear();
    this.retenues.clear();
    this.pourcentages.clear();
    const postes = p.postesPartage?.length ? p.postesPartage : POSTES_PARTAGE_DEFAUT;
    for (const poste of postes) {
      this.postesPartage.push(this.creerPosteGroupe(poste));
    }
    for (const pct of p.pourcentagesRepartition ?? []) {
      this.pourcentages.push(
        this.fb.nonNullable.group({
          membreId: [pct.membreId],
          codeMembre: [pct.codeMembre ?? ''],
          nomComplet: [pct.nomComplet ?? ''],
          pourcentage: [Number(pct.pourcentage), [Validators.required, Validators.min(0.01)]],
        })
      );
    }
    for (const r of p.retenues ?? []) {
      this.retenues.push(
        this.fb.nonNullable.group({
          libelle: [r.libelle, Validators.required],
          typeMode: [r.typeMode],
          valeur: [r.valeur, [Validators.min(0)]],
          ordre: [r.ordre],
        })
      );
    }
    this.form.patchValue({
      cotisationMontantMin: p.cotisationMontantMin,
      cotisationMontantMax: p.cotisationMontantMax,
      partsMin: p.partsMin,
      partsMax: p.partsMax,
      modeRepartition: p.modeRepartition ?? 'PRORATA',
      modeAgregationPostes: p.modeAgregationPostes ?? 'SEPARER',
      modeCalculProrata: p.modeCalculProrata ?? 'PARTS',
      exclureMembresPretEnCours: p.exclureMembresPretEnCours ?? false,
      fraisClotureType: p.fraisClotureType,
      fraisClotureValeur: p.fraisClotureValeur,
    });
  }
}
