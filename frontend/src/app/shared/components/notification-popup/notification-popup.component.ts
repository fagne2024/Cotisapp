import {
  Component,
  effect,
  ElementRef,
  HostListener,
  inject,
  viewChild,
} from '@angular/core';
import { NotificationService } from '../../../core/services/notification.service';

@Component({
  selector: 'app-notification-popup',
  standalone: true,
  templateUrl: './notification-popup.component.html',
  styleUrl: './notification-popup.component.scss',
})
export class NotificationPopupComponent {
  readonly notify = inject(NotificationService);
  private readonly okBtn = viewChild<ElementRef<HTMLButtonElement>>('okBtn');

  constructor() {
    effect(() => {
      if (this.notify.state().visible) {
        queueMicrotask(() => this.okBtn()?.nativeElement.focus());
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.notify.state().visible) {
      this.notify.dismiss();
    }
  }

  iconForKind(): string {
    switch (this.notify.state().kind) {
      case 'success':
        return '✅';
      case 'error':
        return '⚠️';
      default:
        return 'ℹ️';
    }
  }
}
