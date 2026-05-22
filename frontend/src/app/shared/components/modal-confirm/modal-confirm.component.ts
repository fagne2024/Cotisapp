import {
  Component,
  effect,
  ElementRef,
  HostListener,
  inject,
  viewChild,
} from '@angular/core';
import { ConfirmDialogService } from '../../../core/services/confirm-dialog.service';

@Component({
  selector: 'app-modal-confirm',
  standalone: true,
  templateUrl: './modal-confirm.component.html',
  styleUrl: './modal-confirm.component.scss',
})
export class ModalConfirmComponent {
  readonly confirm = inject(ConfirmDialogService);
  private readonly cancelBtn = viewChild<ElementRef<HTMLButtonElement>>('cancelBtn');

  constructor() {
    effect(() => {
      if (this.confirm.state().visible) {
        queueMicrotask(() => this.cancelBtn()?.nativeElement.focus());
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.confirm.state().visible) {
      this.confirm.cancel();
    }
  }
}
