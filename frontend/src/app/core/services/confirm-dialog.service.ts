import { Injectable, signal } from '@angular/core';

export type ConfirmVariant = 'default' | 'danger';

export interface ConfirmDialogState {
  visible: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel: string;
  variant: ConfirmVariant;
}

export interface ConfirmOptions {
  title?: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: ConfirmVariant;
}

const INITIAL: ConfirmDialogState = {
  visible: false,
  title: 'Confirmation',
  message: '',
  confirmLabel: 'Confirmer',
  cancelLabel: 'Annuler',
  variant: 'default',
};

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  readonly state = signal<ConfirmDialogState>({ ...INITIAL });

  private resolver?: (value: boolean) => void;

  confirm(options: ConfirmOptions): Promise<boolean> {
    if (this.state().visible) {
      return Promise.resolve(false);
    }
    return new Promise<boolean>((resolve) => {
      this.resolver = resolve;
      this.state.set({
        visible: true,
        title: options.title ?? 'Confirmation',
        message: options.message.trim(),
        confirmLabel: options.confirmLabel ?? 'Confirmer',
        cancelLabel: options.cancelLabel ?? 'Annuler',
        variant: options.variant ?? 'default',
      });
    });
  }

  accept(): void {
    this.finish(true);
  }

  cancel(): void {
    this.finish(false);
  }

  private finish(result: boolean): void {
    this.state.set({ ...INITIAL });
    const resolve = this.resolver;
    this.resolver = undefined;
    resolve?.(result);
  }
}
