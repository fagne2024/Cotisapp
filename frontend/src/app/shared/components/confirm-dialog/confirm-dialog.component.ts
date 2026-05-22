import { Component, effect, input, output } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

export type ConfirmDialogVariant = 'warn' | 'danger' | 'info';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './confirm-dialog.component.html',
  styleUrl: './confirm-dialog.component.scss',
})
export class ConfirmDialogComponent {
  readonly title = input.required<string>();
  readonly paragraphs = input.required<string[]>();
  readonly variant = input<ConfirmDialogVariant>('warn');
  readonly showCancel = input(true);
  readonly confirmLabel = input('Confirmer');
  readonly cancelLabel = input('Annuler');
  readonly loading = input(false);
  readonly showMotifField = input(false);
  readonly motifLabel = input('Motif (optionnel)');
  readonly motifPlaceholder = input('');

  readonly confirmed = output<void>();
  readonly confirmedWithMotif = output<string>();
  readonly cancelled = output<void>();

  readonly motifCtrl = new FormControl('', { nonNullable: false });

  constructor() {
    effect(() => {
      if (this.loading()) {
        this.motifCtrl.disable({ emitEvent: false });
      } else {
        this.motifCtrl.enable({ emitEvent: false });
      }
    });
  }

  onConfirm(): void {
    if (this.loading()) {
      return;
    }
    if (this.showMotifField()) {
      const texte = (this.motifCtrl.value ?? '').trim();
      this.confirmedWithMotif.emit(texte);
      this.motifCtrl.reset('');
      return;
    }
    this.confirmed.emit();
  }

  onCancel(): void {
    if (!this.loading()) {
      this.motifCtrl.reset('');
      this.cancelled.emit();
    }
  }
}
