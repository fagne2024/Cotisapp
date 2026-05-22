import { Component, computed, input, output } from '@angular/core';
import {
  buildPageNumbers,
  buildRangeLabel,
  clampPage,
  paginationTotalPages,
} from '../../util/pagination.util';

@Component({
  selector: 'app-list-pagination',
  standalone: true,
  template: `
    @if (afficher()) {
      <div class="pagination" [class.no-print]="noPrint()">
        <span>{{ rangeLabel() }}</span>
        <div class="page-btns">
          <button
            type="button"
            class="page-btn"
            (click)="changerPage(page() - 1)"
            [disabled]="page() <= 1"
            aria-label="Page précédente"
          >
            ‹
          </button>
          @for (pn of pageNumbers(); track pn) {
            <button
              type="button"
              class="page-btn"
              [class.on]="page() === pn"
              (click)="changerPage(pn)"
              [attr.aria-current]="page() === pn ? 'page' : null"
            >
              {{ pn }}
            </button>
          }
          <button
            type="button"
            class="page-btn"
            (click)="changerPage(page() + 1)"
            [disabled]="page() >= totalPages()"
            aria-label="Page suivante"
          >
            ›
          </button>
        </div>
      </div>
    }
  `,
  styleUrls: ['../../styles/pagination.scss'],
})
export class ListPaginationComponent {
  readonly page = input.required<number>();
  readonly total = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly unit = input('élément(s)');
  readonly noPrint = input(true);

  readonly pageChange = output<number>();

  readonly totalPages = computed(() => paginationTotalPages(this.total(), this.pageSize()));
  readonly afficher = computed(() => this.total() > this.pageSize());
  readonly pageNumbers = computed(() => buildPageNumbers(this.page(), this.totalPages()));
  readonly rangeLabel = computed(() =>
    buildRangeLabel(this.page(), this.total(), this.pageSize(), this.unit())
  );

  changerPage(p: number): void {
    this.pageChange.emit(clampPage(p, this.totalPages()));
  }
}
