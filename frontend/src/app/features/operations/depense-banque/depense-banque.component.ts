import { Component, computed, inject, OnDestroy, OnInit, signal, HostListener } from '@angular/core';

import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ActivatedRoute, Router } from '@angular/router';

import { Subscription } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';

import { DepenseBanqueService } from '../../../core/services/depense-banque.service';

import { NotificationService } from '../../../core/services/notification.service';

import { organisationCouranteId } from '../../../core/util/org-route.util';

import { formatFcfa } from '../../../core/utils/currency.util';

import { FilterQueryNav, qpEnum, qpString } from '../../../shared/util/filter-query.util';

import { matchTextQuery } from '../../../shared/util/filter.util';
import { ListPaginationComponent } from '../../../shared/components/list-pagination/list-pagination.component';
import { paginateSlice } from '../../../shared/util/pagination.util';

import {

  BanqueOpUi,

  CATEGORIES_DEPENSE,

  CategorieDepense,

  DepenseBanquePageUi,

  DepenseParCategorie,

  DepenseRecente,

  MouvementBancaire,
  MouvementCaisse,

} from './depense-banque-demo.util';



@Component({

  selector: 'app-depense-banque',

  standalone: true,

  imports: [ReactiveFormsModule, ListPaginationComponent],

  templateUrl: './depense-banque.component.html',

  styleUrls: ['./depense-banque.component.scss', '../../../shared/styles/pagination.scss'],

})

export class DepenseBanqueComponent implements OnInit, OnDestroy {

  private readonly fb = inject(FormBuilder);

  private readonly route = inject(ActivatedRoute);

  private readonly router = inject(Router);

  readonly auth = inject(AuthService);

  private readonly notify = inject(NotificationService);

  private readonly depenseBanqueApi = inject(DepenseBanqueService);



  readonly formatFcfa = formatFcfa;

  readonly categories = CATEGORIES_DEPENSE;

  readonly pageUi = signal<DepenseBanquePageUi>('dep');

  readonly bkType = signal<BanqueOpUi>('vers');

  readonly categorieId = signal('restauration');

  readonly depensesRecentes = signal<DepenseRecente[]>([]);

  readonly depensesParCat = signal<DepenseParCategorie[]>([]);

  readonly mouvementsBanque = signal<MouvementBancaire[]>([]);

  readonly mouvementsCaisse = signal<MouvementCaisse[]>([]);

  readonly entreesCaisseMois = signal(0);

  readonly sortiesCaisseMois = signal(0);

  readonly soldeCaisse = signal(0);

  readonly soldeBanque = signal(0);

  readonly totalDepensesMois = signal(0);

  readonly chargement = signal(false);

  readonly enregistrement = signal(false);

  readonly releveFichier = signal<File | null>(null);
  readonly releveFichierNom = computed(() => this.releveFichier()?.name ?? '');
  readonly releveApercuUrl = signal<string | null>(null);
  /** Mobile / tablette (≤ 1024 px). */
  readonly appareilMobileOuTablette = signal(false);
  readonly afficherCaptureBordereau = computed(
    () => this.appareilMobileOuTablette() && this.bkType() === 'vers'
  );

  /** Force la réévaluation des prévisualisations quand le formulaire ou les soldes changent. */
  private readonly formPreviewTick = signal(0);

  readonly soldesCharges = signal(false);



  readonly formDep = this.fb.nonNullable.group({

    montant: [25_000, [Validators.required, Validators.min(1)]],

    compteDebite: ['caisse'],

    beneficiaire: [''],

    dateDepense: [this.todayIso(), Validators.required],

    description: [''],

    saisiPar: [{ value: '', disabled: true }],

  });



  readonly formBk = this.fb.nonNullable.group({

    montant: [0, [Validators.required, Validators.min(1)]],

    dateOperation: [this.todayIso(), Validators.required],

    reference: [''],

    banqueAgence: [''],

    description: [''],

    validePar: [{ value: '', disabled: true }],

    contreSigne: [''],

  });



  readonly filtreDepCategorie = signal('tous');

  readonly filtreDepRecherche = signal('');

  readonly filtreBkMvtType = signal<'tous' | BanqueOpUi>('tous');

  readonly filtreCaisSens = signal<'tous' | 'credit' | 'debit'>('tous');

  readonly pageCaisse = signal(1);
  readonly pageDep = signal(1);
  readonly pageBanque = signal(1);
  readonly pageSizeListe = 15;

  readonly depensesRecentesFiltrees = computed(() => {

    const cat = this.filtreDepCategorie();

    const q = this.filtreDepRecherche();

    return this.depensesRecentes().filter((d) => {

      if (cat !== 'tous' && d.categorieId !== cat) return false;

      return matchTextQuery(q, d.categorie, d.dateLabel, d.beneficiaire ?? '');

    });

  });



  readonly mouvementsBanqueFiltres = computed(() => {

    const t = this.filtreBkMvtType();

    return this.mouvementsBanque().filter((m) => t === 'tous' || m.type === t);

  });

  readonly mouvementsCaisseFiltres = computed(() => {
    const s = this.filtreCaisSens();
    return this.mouvementsCaisse().filter((m) => s === 'tous' || m.sens === s);
  });

  readonly mouvementsCaissePaged = computed(() =>
    paginateSlice(this.mouvementsCaisseFiltres(), this.pageCaisse(), this.pageSizeListe)
  );

  readonly depensesRecentesPaged = computed(() =>
    paginateSlice(this.depensesRecentesFiltrees(), this.pageDep(), this.pageSizeListe)
  );

  readonly mouvementsBanquePaged = computed(() =>
    paginateSlice(this.mouvementsBanqueFiltres(), this.pageBanque(), this.pageSizeListe)
  );

  readonly categorieSelectionnee = computed(() => {

    const id = this.categorieId();

    return this.categories.find((c) => c.id === id) ?? this.categories[0];

  });



  readonly depPreview = computed(() => {
    this.formPreviewTick();
    const caisse = this.soldeCaisse();
    const banque = this.soldeBanque();
    const m = Math.max(0, Number(this.formDep.controls.montant.value) || 0);
    const compte = this.formDep.controls.compteDebite.value ?? 'caisse';
    const debiteBanque = compte === 'banque';
    const soldeDebite = debiteBanque ? banque : caisse;
    return {
      montant: m,
      debiteLabel: debiteBanque ? 'Organisation · Banque' : 'Organisation · Caisse',
      caisseActuelle: caisse,
      banqueActuelle: banque,
      soldeDebiteActuel: soldeDebite,
      soldeDebiteApres: soldeDebite - m,
      debiteBanque,
    };
  });

  readonly bkPreview = computed(() => {
    this.formPreviewTick();
    this.bkType();
    const caisse = this.soldeCaisse();
    const banque = this.soldeBanque();
    const m = Math.max(0, Number(this.formBk.controls.montant.value) || 0);
    const vers = this.bkType() === 'vers';
    return {
      montant: m,
      type: vers ? ('vers' as const) : ('ret' as const),
      debitLabel: vers ? 'Organisation · Caisse' : 'Organisation · Banque',
      creditLabel: vers ? 'Organisation · Banque' : 'Organisation · Caisse',
      caisseActuelle: caisse,
      banqueActuelle: banque,
      caisseApres: vers ? caisse - m : caisse + m,
      banqueApres: vers ? banque + m : banque - m,
    };
  });



  private orgId = 0;

  private sub = new Subscription();

  private mqMobileTablette: MediaQueryList | null = null;

  private readonly onMqMobileTablette = (): void => {
    this.appareilMobileOuTablette.set(this.mqMobileTablette?.matches ?? false);
  };

  private prevPage: DepenseBanquePageUi | null = null;

  private readonly queryNav = new FilterQueryNav();

  private readonly queryDefaults = { cat: 'tous', q: '', mvt: 'tous' };



  ngOnInit(): void {

    const nom = this.auth.nomComplet();

    const role = this.auth.currentRole() === 'ADMIN_GIE' ? 'Admin GIE' : 'Utilisateur';

    this.formDep.patchValue({ saisiPar: `${nom} (${role})` });

    this.formBk.patchValue({ validePar: `${nom} (${role})` });



    this.orgId = organisationCouranteId(this.route, this.auth) ?? 0;

    if (typeof window !== 'undefined' && window.matchMedia) {
      this.mqMobileTablette = window.matchMedia('(max-width: 1024px)');
      this.onMqMobileTablette();
      this.mqMobileTablette.addEventListener('change', this.onMqMobileTablette);
    }

    if (this.orgId > 0) {

      this.chargerDonnees();

    }

    this.sub.add(
      this.formDep.valueChanges.subscribe(() => this.formPreviewTick.update((n) => n + 1))
    );
    this.sub.add(
      this.formBk.valueChanges.subscribe(() => this.formPreviewTick.update((n) => n + 1))
    );

    this.sub.add(

      this.route.queryParamMap.subscribe((pm) => {

        const p = this.parsePage(pm.get('p'));

        const changed = this.prevPage !== null && this.prevPage !== p;

        this.prevPage = p;

        this.pageUi.set(p);

        if (changed && p === 'bk') {

          this.bkType.set('vers');

          this.formBk.patchValue({

            description: 'Versement hebdomadaire caisse en banque',

            montant: 0,

          });

        }

        this.queryNav.runSync(() => {

          const cat = qpString(pm, 'cat', 32);

          this.filtreDepCategorie.set(cat || 'tous');

          this.filtreDepRecherche.set(qpString(pm, 'q'));

          this.filtreBkMvtType.set(

            qpEnum(pm, 'mvt', ['tous', 'vers', 'ret'] as const, 'tous')

          );

        });

      })

    );

  }



  ngOnDestroy(): void {

    this.sub.unsubscribe();

    this.queryNav.destroy();

    this.mqMobileTablette?.removeEventListener('change', this.onMqMobileTablette);

    this.revoquerApercuReleve();

  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey) {
      if (event.key === '1') {
        event.preventDefault();
        this.pageUi.set('cais');
      } else if (event.key === '2') {
        event.preventDefault();
        this.pageUi.set('dep');
      } else if (event.key === '3') {
        event.preventDefault();
        this.pageUi.set('bk');
      }
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.formDep.reset();
      this.formBk.reset();
    }
  }



  private chargerDonnees(): void {

    this.chargement.set(true);

    this.depenseBanqueApi.chargerTableauDeBord(this.orgId).subscribe({

      next: (d) => {

        this.soldeCaisse.set(Number(d.soldeCaisse) || 0);

        this.soldeBanque.set(Number(d.soldeBanque) || 0);

        this.totalDepensesMois.set(Number(d.totalDepensesMois) || 0);

        this.depensesRecentes.set(

          (d.depensesRecentes ?? []).map((l) => ({

            categorieId: l.categorieId,

            categorie: l.categorieLabel,

            montant: Number(l.montant),

            dateLabel: this.formatDateLabel(l.dateOperation),

            beneficiaire: l.beneficiaire,

          }))

        );

        this.depensesParCat.set(

          (d.depensesParCategorie ?? []).map((c) => ({

            icon: c.icon,

            label: c.label,

            montant: Number(c.montant),

          }))

        );

        this.mouvementsBanque.set(

          (d.mouvementsBanque ?? []).map((m) => ({

            dateLabel: this.formatDateLabel(m.dateOperation),

            type: m.type === 'ret' ? 'ret' : 'vers',

            montant: Number(m.montant),

            soldeApres: Number(m.soldeBanqueApres),
            releveId: m.releveId,
            releveNomFichier: m.releveNomFichier,

          }))

        );

        this.entreesCaisseMois.set(Number(d.entreesCaisseMois) || 0);
        this.sortiesCaisseMois.set(Number(d.sortiesCaisseMois) || 0);
        this.mouvementsCaisse.set(
          (d.mouvementsCaisse ?? []).map((m) => ({
            id: m.id,
            dateLabel: this.formatDateLabel(m.dateOperation),
            sens: m.sens === 'credit' ? 'credit' : 'debit',
            montant: Number(m.montant),
            soldeApres: Number(m.soldeCaisseApres),
            typeOperation: m.typeOperation,
            libelle: m.libelle,
          }))
        );

        this.chargement.set(false);
        this.soldesCharges.set(true);
        this.resetPaginationListes();
        this.formPreviewTick.update((n) => n + 1);

      },

      error: (err) => {

        this.chargement.set(false);
        this.soldesCharges.set(false);

        this.showToast(err?.error?.message ?? 'Impossible de charger les données.');

      },

    });

  }



  private pushFiltersToUrl(debounce = false): void {

    this.queryNav.push(

      this.router,

      this.route,

      {

        cat: this.filtreDepCategorie(),

        q: this.filtreDepRecherche(),

        mvt: this.filtreBkMvtType(),

      },

      this.queryDefaults,

      debounce ? 400 : 0

    );

  }



  onFiltreDepCategorie(ev: Event): void {

    this.filtreDepCategorie.set((ev.target as HTMLSelectElement).value);

    this.pageDep.set(1);

    this.pushFiltersToUrl();

  }



  onFiltreDepRecherche(ev: Event): void {

    this.filtreDepRecherche.set((ev.target as HTMLInputElement).value);

    this.pageDep.set(1);

    this.pushFiltersToUrl(true);

  }



  onFiltreBkMvtType(ev: Event): void {

    const v = (ev.target as HTMLSelectElement).value;

    this.filtreBkMvtType.set(v === 'vers' || v === 'ret' ? v : 'tous');

    this.pageBanque.set(1);

    this.pushFiltersToUrl();

  }

  onFiltreCaisSens(ev: Event): void {
    const v = (ev.target as HTMLSelectElement).value;
    this.filtreCaisSens.set(v === 'credit' || v === 'debit' ? v : 'tous');
    this.pageCaisse.set(1);
  }

  private resetPaginationListes(): void {
    this.pageCaisse.set(1);
    this.pageDep.set(1);
    this.pageBanque.set(1);
  }

  private parsePage(raw: string | null): DepenseBanquePageUi {
    if (raw === 'dep' || raw === 'bk' || raw === 'cais') {
      return raw;
    }
    return 'cais';
  }

  setPage(p: DepenseBanquePageUi): void {

    void this.router.navigate([], {

      relativeTo: this.route,

      queryParams: { p },

      queryParamsHandling: 'merge',

      replaceUrl: true,

    });

  }



  selectCategorie(c: CategorieDepense): void {

    this.categorieId.set(c.id);

  }



  isCategorieOn(c: CategorieDepense): boolean {

    return this.categorieId() === c.id;

  }



  setBkType(t: BanqueOpUi): void {

    this.bkType.set(t);
    this.formPreviewTick.update((n) => n + 1);

    this.formBk.patchValue({

      description:

        t === 'vers'

          ? 'Versement hebdomadaire caisse en banque'

          : 'Retrait banque pour besoins en espèces',

    });

  }



  annulerDep(): void {

    this.formDep.patchValue({

      montant: 25_000,

      compteDebite: 'caisse',

      beneficiaire: '',

      dateDepense: this.todayIso(),

      description: '',

    });

    this.categorieId.set('restauration');

  }



  annulerBk(): void {

    this.bkType.set('vers');
    this.releveFichier.set(null);
    this.revoquerApercuReleve();
    this.reinitialiserInputReleve();

    this.formBk.patchValue({

      montant: 0,

      dateOperation: this.todayIso(),

      reference: '',

      banqueAgence: '',

      description: 'Versement hebdomadaire caisse en banque',

      contreSigne: '',

    });

  }

  onReleveBancaireSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      this.releveFichier.set(null);
      this.revoquerApercuReleve();
      return;
    }
    const max = 10 * 1024 * 1024;
    if (file.size > max) {
      this.showToast('Le fichier dépasse 10 Mo.');
      input.value = '';
      this.releveFichier.set(null);
      this.revoquerApercuReleve();
      return;
    }
    const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
    const mime = file.type.toLowerCase();
    const imageMime = mime.startsWith('image/');
    const okExt = ['pdf', 'png', 'jpg', 'jpeg', 'webp', 'heic', 'heif'].includes(ext);
    if (!okExt && !imageMime) {
      this.showToast('Format non autorisé (PDF, PNG, JPEG, WEBP).');
      input.value = '';
      this.releveFichier.set(null);
      this.revoquerApercuReleve();
      return;
    }
    this.releveFichier.set(file);
    this.majApercuReleve(file);
  }

  declencherCaptureBordereau(): void {
    (document.getElementById('bk-releve-camera') as HTMLInputElement | null)?.click();
  }

  declencherFichierBordereau(): void {
    (document.getElementById('bk-releve') as HTMLInputElement | null)?.click();
  }

  retirerReleveBordereau(): void {
    this.releveFichier.set(null);
    this.revoquerApercuReleve();
    this.reinitialiserInputReleve();
  }

  private majApercuReleve(file: File): void {
    this.revoquerApercuReleve();
    if (file.type.startsWith('image/')) {
      this.releveApercuUrl.set(URL.createObjectURL(file));
    }
  }

  private revoquerApercuReleve(): void {
    const url = this.releveApercuUrl();
    if (url) {
      URL.revokeObjectURL(url);
    }
    this.releveApercuUrl.set(null);
  }

  telechargerReleve(releveId: number, nom?: string): void {
    if (this.orgId <= 0) return;
    this.depenseBanqueApi.telechargerReleve(this.orgId, releveId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = nom || 'releve-bancaire';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.showToast('Impossible de télécharger le relevé.'),
    });
  }

  private reinitialiserInputReleve(): void {
    for (const id of ['bk-releve', 'bk-releve-camera']) {
      const el = document.getElementById(id) as HTMLInputElement | null;
      if (el) el.value = '';
    }
  }



  validerDep(): void {

    if (this.orgId <= 0) {

      this.showToast('Organisation non sélectionnée.');

      return;

    }

    if (this.formDep.invalid) {

      this.showToast('Veuillez vérifier le montant et les champs obligatoires.');

      return;

    }

    const raw = this.formDep.getRawValue();

    this.enregistrement.set(true);

    this.depenseBanqueApi

      .enregistrerDepense(this.orgId, {

        montant: raw.montant,

        compteDebite: raw.compteDebite,

        beneficiaire: raw.beneficiaire || undefined,

        dateDepense: raw.dateDepense,

        description: raw.description || undefined,

        categorieId: this.categorieId(),

      })

      .subscribe({

        next: () => {

          this.enregistrement.set(false);

          this.showToast('✅ Dépense enregistrée !');

          this.annulerDep();

          this.chargerDonnees();

        },

        error: (err) => {

          this.enregistrement.set(false);

          this.showToast(err?.error?.message ?? "Erreur lors de l'enregistrement.");

        },

      });

  }



  validerBk(): void {

    if (this.orgId <= 0) {

      this.showToast('Organisation non sélectionnée.');

      return;

    }

    if (this.formBk.invalid) {

      this.showToast('Veuillez vérifier le montant et les champs obligatoires.');

      return;

    }

    const raw = this.formBk.getRawValue();

    const t = this.bkType();

    this.enregistrement.set(true);

    this.depenseBanqueApi

      .enregistrerBanque(
        this.orgId,
        {
          montant: raw.montant,
          type: t,
          dateOperation: raw.dateOperation,
          reference: raw.reference || undefined,
          banqueAgence: raw.banqueAgence || undefined,
          description: raw.description || undefined,
          contreSigne: raw.contreSigne || undefined,
        },
        this.releveFichier() ?? undefined
      )

      .subscribe({

        next: () => {

          this.enregistrement.set(false);

          this.showToast(

            t === 'vers' ? '✅ Versement en banque confirmé !' : '✅ Retrait bancaire confirmé !'

          );

          this.annulerBk();

          this.chargerDonnees();

        },

        error: (err) => {

          this.enregistrement.set(false);

          this.showToast(err?.error?.message ?? "Erreur lors de l'enregistrement.");

        },

      });

  }

  mouvementSigne(m: MouvementBancaire): string {

    return m.type === 'vers' ? '+' : '−';

  }



  private formatDateLabel(iso: string): string {

    if (!iso) return '';

    const [y, mo, d] = iso.split('-').map(Number);

    if (!y || !mo || !d) return iso;

    return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit' }).format(

      new Date(y, mo - 1, d)

    );

  }



  private showToast(msg: string): void {

    this.notify.show(msg);

  }



  private todayIso(): string {

    const d = new Date();

    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

  }

}


