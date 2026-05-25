import { DecimalPipe } from '@angular/common';
import { Component, computed, DestroyRef, effect, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, distinctUntilChanged, startWith } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import {
  MembrePourcentageRepartitionDto,
  ModeCalculProrataCloture,
  ModeRepartitionCloture,
  ParametrageClotureDto,
  ParametrageClotureService,
  BUILTIN_POSTES_CLOTURE,
  BuiltinPosteCloture,
  POSTES_PARTAGE_DEFAUT,
  PerimetrePartagePreset,
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
import { DROIT_ACTION_IMPORTS } from '../../shared/imports/droit-action.imports';

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
    ...DROIT_ACTION_IMPORTS,
  ],
  templateUrl: './parametrage-cloture.component.html',
  styleUrls: [
    './parametrage-cloture.component.scss',
    '../../shared/styles/pagination.scss',
  ],
})
export class ParametrageClotureComponent implements OnInit {
  readonly Math = Math;
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ParametrageClotureService);
  private readonly membreService = inject(MembreService);
  private readonly notify = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly formatFcfa = formatFcfa;
  readonly chargement = signal(true);
  readonly enregistrement = signal(false);
  readonly autoSauvegarde = signal(false);
  readonly sauvegardeAutoOk = signal(false);
  readonly previewRepartition = signal<PreviewClotureExerciceDto | null>(null);
  readonly previewLoading = signal(false);
  private chargementInitial = true;
  readonly pctPageSize = 10;
  readonly pctPage = signal(1);
  readonly modeRepartitionUi = signal<ModeRepartitionCloture>('PRORATA');
  readonly modeCalculProrataUi = signal<ModeCalculProrataCloture>('PARTS');
  readonly builtinPostes = BUILTIN_POSTES_CLOTURE;
  readonly libellesBuiltin: Record<BuiltinPosteCloture, string> = {
    INTERETS: "Intérêts / frais d'emprunt",
    PENALITES: 'Pénalités de retard',
    AMENDES: 'Amendes sur cotisations',
  };

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
        if (
          p.modeCalculProrata === 'POURCENTAGE' &&
          (!p.pourcentagesRepartition || p.pourcentagesRepartition.length === 0)
        ) {
          this.chargerPourcentagesMembres(orgId);
        }
        this.chargerPreviewRepartition();
        this.chargementInitial = false;
        this.brancherSynchronisationFormulaire();
      },
      error: () => {
        this.chargement.set(false);
        this.notify.error('Impossible de charger le paramétrage de clôture.');
      },
    });
  }

  postesPersonnalises(): number[] {
    return this.postesPartage.controls
      .map((_, i) => i)
      .filter((i) => !this.posteEstBuiltin(i));
  }

  posteCtrl(index: number) {
    return this.postesPartage.at(index);
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

  indexPosteBuiltin(code: BuiltinPosteCloture): number {
    return this.postesPartage.controls.findIndex((c) => c.get('code')?.value === code);
  }

  posteBuiltinActif(code: BuiltinPosteCloture): boolean {
    const i = this.indexPosteBuiltin(code);
    return i >= 0 && !!this.postesPartage.at(i).get('actif')?.value;
  }

  posteBuiltinPool(code: BuiltinPosteCloture): boolean {
    const i = this.indexPosteBuiltin(code);
    return i >= 0 && !!this.postesPartage.at(i).get('inclureDansPoolAdditionne')?.value;
  }

  posteBuiltinProrata(code: BuiltinPosteCloture): boolean {
    const i = this.indexPosteBuiltin(code);
    return i >= 0 && !!this.postesPartage.at(i).get('appliquerProrata')?.value;
  }

  patchPosteBuiltin(
    code: BuiltinPosteCloture,
    patch: Partial<{
      actif: boolean;
      inclureDansPoolAdditionne: boolean;
      appliquerProrata: boolean;
      groupePartage: number;
    }>
  ): void {
    const i = this.indexPosteBuiltin(code);
    if (i >= 0) {
      this.postesPartage.at(i).patchValue(patch);
    }
  }

  onBuiltinActifChange(code: BuiltinPosteCloture, checked: boolean): void {
    this.patchPosteBuiltin(code, { actif: checked });
  }

  onBuiltinPoolChange(code: BuiltinPosteCloture, checked: boolean): void {
    this.patchPosteBuiltin(code, { inclureDansPoolAdditionne: checked });
  }

  onBuiltinProrataChange(code: BuiltinPosteCloture, checked: boolean): void {
    this.patchPosteBuiltin(code, { appliquerProrata: checked });
  }

  perimetrePresetActuel(): PerimetrePartagePreset | 'CUSTOM' {
    const i = this.posteBuiltinActif('INTERETS');
    const p = this.posteBuiltinActif('PENALITES');
    const a = this.posteBuiltinActif('AMENDES');
    if (i && !p && !a) return 'INTERETS_SEUL';
    if (!i && p && a) return 'SANCTIONS_SEUL';
    if (i && p && a) return 'TOUS';
    return 'CUSTOM';
  }

  appliquerPerimetre(preset: PerimetrePartagePreset): void {
    switch (preset) {
      case 'INTERETS_SEUL':
        this.patchPosteBuiltin('INTERETS', { actif: true });
        this.patchPosteBuiltin('PENALITES', { actif: false });
        this.patchPosteBuiltin('AMENDES', { actif: false });
        break;
      case 'SANCTIONS_SEUL':
        this.patchPosteBuiltin('INTERETS', { actif: false });
        this.patchPosteBuiltin('PENALITES', { actif: true });
        this.patchPosteBuiltin('AMENDES', { actif: true });
        break;
      case 'TOUS':
        this.patchPosteBuiltin('INTERETS', { actif: true });
        this.patchPosteBuiltin('PENALITES', { actif: true });
        this.patchPosteBuiltin('AMENDES', { actif: true });
        break;
    }
  }

  montantPoolApercu(code: BuiltinPosteCloture): number | null {
    const prev = this.previewRepartition();
    if (!prev) return null;
    const p = prev.postes?.find((x) => x.code === code);
    if (p?.montantPool != null) return p.montantPool;
    if (code === 'INTERETS') return prev.poolInterets ?? null;
    if (code === 'PENALITES') return prev.poolPenalites ?? null;
    if (code === 'AMENDES') return prev.poolAmendes ?? null;
    return null;
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
      appliquerProrata: true,
    }));
  }

  toutAdditionnerAuPool(): void {
    for (const code of BUILTIN_POSTES_CLOTURE) {
      if (this.posteBuiltinActif(code)) {
        this.patchPosteBuiltin(code, { inclureDansPoolAdditionne: true });
      }
    }
    this.postesPartage.controls.forEach((c) => {
      if (c.get('actif')?.value && !c.get('builtIn')?.value) {
        c.patchValue({ inclureDansPoolAdditionne: true });
      }
    });
  }

  rienAdditionnerAuPool(): void {
    for (const code of BUILTIN_POSTES_CLOTURE) {
      this.patchPosteBuiltin(code, { inclureDansPoolAdditionne: false });
    }
    this.postesPartage.controls.forEach((c) => {
      if (!c.get('builtIn')?.value) {
        c.patchValue({ inclureDansPoolAdditionne: false });
      }
    });
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

  onModeCalculProrataChoisi(mode: ModeCalculProrataCloture): void {
    this.modeCalculProrataUi.set(mode);
    if (mode === 'POURCENTAGE' && this.pourcentages.length === 0) {
      const orgId = this.orgCourante();
      if (orgId) this.chargerPourcentagesMembres(orgId);
    }
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

  enregistrer(manuel = true): void {
    const body = this.construireCorps();
    if (!body) {
      if (manuel) {
        this.form.markAllAsTouched();
        if (this.form.invalid) return;
        this.notify.error('Corrigez le formulaire avant enregistrement.');
      }
      return;
    }
    const orgId = organisationCouranteId(this.route, this.auth) ?? 0;
    if (orgId < 1) return;
    if (manuel) {
      this.enregistrement.set(true);
    } else {
      this.autoSauvegarde.set(true);
    }
    this.api.enregistrer(orgId, body).subscribe({
      next: () => {
        this.enregistrement.set(false);
        this.autoSauvegarde.set(false);
        this.sauvegardeAutoOk.set(true);
        if (manuel) {
          this.notify.success('Paramétrage de clôture enregistré.');
        }
      },
      error: (err) => {
        this.enregistrement.set(false);
        this.autoSauvegarde.set(false);
        this.sauvegardeAutoOk.set(false);
        if (manuel) {
          this.notify.error(err?.error?.message ?? 'Enregistrement impossible.');
        }
      },
    });
  }

  chargerPreviewRepartition(): void {
    const orgId = organisationCouranteId(this.route, this.auth) ?? 0;
    if (orgId < 1) return;
    const body = this.construireCorps();
    if (!body) {
      this.previewRepartition.set(null);
      return;
    }
    this.previewLoading.set(true);
    this.api.previewRepartitionAvecParametrage(orgId, body).subscribe({
      next: (p) => {
        this.previewRepartition.set(p);
        this.previewLoading.set(false);
      },
      error: (err) => {
        this.previewRepartition.set(null);
        this.previewLoading.set(false);
        if (!this.chargementInitial) {
          const msg = err?.error?.message;
          if (msg) this.notify.error(msg);
        }
      },
    });
  }

  private brancherSynchronisationFormulaire(): void {
    this.form.controls.modeRepartition.valueChanges
      .pipe(startWith(this.form.controls.modeRepartition.value), takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => this.modeRepartitionUi.set(v ?? 'PRORATA'));

    this.form.controls.modeCalculProrata.valueChanges
      .pipe(startWith(this.form.controls.modeCalculProrata.value), takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => {
        const mode = v ?? 'PARTS';
        this.modeCalculProrataUi.set(mode);
        if (mode === 'POURCENTAGE' && this.pourcentages.length === 0) {
          const orgId = this.orgCourante();
          if (orgId) this.chargerPourcentagesMembres(orgId);
        }
      });

    this.form.valueChanges
      .pipe(
        debounceTime(450),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => {
        if (this.chargementInitial) return;
        this.chargerPreviewRepartition();
      });

    this.form.valueChanges
      .pipe(
        debounceTime(1400),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => {
        if (this.chargementInitial || this.form.invalid) return;
        if (!this.construireCorps()) return;
        this.enregistrer(false);
      });
  }

  private construireCorps(): ParametrageClotureDto | null {
    if (this.form.invalid) return null;
    const orgId = organisationCouranteId(this.route, this.auth) ?? 0;
    if (orgId < 1) return null;
    const raw = this.form.getRawValue();
    const postes = raw.postesPartage as PostePartageClotureDto[];
    if (!postes.some((p) => p.actif)) return null;
    if (
      raw.modeRepartition === 'PRORATA' &&
      raw.modeCalculProrata === 'POURCENTAGE' &&
      Math.abs(this.sommePourcentages() - 100) > 0.01
    ) {
      return null;
    }
    if (
      raw.modeAgregationPostes === 'ADDITIONNER' &&
      !postes.some((p) => p.actif && p.inclureDansPoolAdditionne)
    ) {
      return null;
    }
    return {
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
        appliquerProrata:
          raw.modeRepartition === 'PRORATA' ? p.appliquerProrata !== false : true,
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
      appliquerProrata: [p.appliquerProrata !== false],
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
    this.modeRepartitionUi.set(p.modeRepartition ?? 'PRORATA');
    this.modeCalculProrataUi.set(p.modeCalculProrata ?? 'PARTS');
  }
}
