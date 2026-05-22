import { Component, computed, effect, input, output, signal } from '@angular/core';
import { ListPaginationComponent } from '../../shared/components/list-pagination/list-pagination.component';
import { paginateSlice } from '../../shared/util/pagination.util';
import { PreviewClotureExerciceDto } from '../../core/services/parametrage-cloture.service';
import { formatFcfa } from '../../core/utils/currency.util';
import {
  libelleCompteCourt,
  colonnesDistributionPreview,
  libelleAgregationPostes,
  libelleModeRepartition,
  montantPosteMembre,
  postesActifsPreview,
} from './cloture-preview.util';
import { PostePartageClotureDto } from '../../core/services/parametrage-cloture.service';

@Component({
  selector: 'app-cloture-repartition-preview',
  standalone: true,
  imports: [ListPaginationComponent],
  templateUrl: './cloture-repartition-preview.component.html',
  styleUrls: [
    './cloture-repartition-preview.component.scss',
    '../../shared/styles/pagination.scss',
  ],
})
export class ClotureRepartitionPreviewComponent {
  readonly pageSize = 10;
  readonly page = signal(1);
  readonly preview = input<PreviewClotureExerciceDto | null>(null);
  readonly loading = input(false);
  readonly showRefresh = input(true);

  readonly refresh = output<void>();

  readonly formatFcfa = formatFcfa;
  readonly libelleModeRepartition = libelleModeRepartition;
  readonly postesActifsPreview = postesActifsPreview;
  readonly colonnesDistributionPreview = colonnesDistributionPreview;
  readonly libelleAgregationPostes = libelleAgregationPostes;
  readonly montantPosteMembre = montantPosteMembre;
  readonly libelleCompteCourt = libelleCompteCourt;

  readonly membresTotal = computed(() => this.preview()?.membres?.length ?? 0);

  readonly membresPage = computed(() => {
    const prev = this.preview();
    if (!prev?.membres?.length) return [];
    return paginateSlice(prev.membres, this.page(), this.pageSize);
  });

  constructor() {
    effect(() => {
      this.preview();
      this.page.set(1);
    });
  }

  onRefresh(): void {
    this.refresh.emit();
  }

  aDesRetenues(p: PreviewClotureExerciceDto): boolean {
    const frais = Number(p.fraisCloture) || 0;
    if (frais > 0) return true;
    return (p.retenues ?? []).some((r) => (Number(r.montantCalcule) || 0) > 0);
  }

  fraisCloturePositif(p: PreviewClotureExerciceDto): boolean {
    return (Number(p.fraisCloture) || 0) > 0;
  }

  montantRetenuePositif(montant: number | undefined): boolean {
    return (Number(montant) || 0) > 0;
  }

  posteSansMontant(poste: PostePartageClotureDto): boolean {
    return (Number(poste.montantPool) || 0) <= 0;
  }

  membresEligibles(p: PreviewClotureExerciceDto): number {
    return (p.membres ?? []).filter((m) => !m.excluDuPartage).length;
  }
}
