import { Injectable, signal } from '@angular/core';

export type NotificationKind = 'success' | 'error' | 'info';

export interface NotificationState {
  visible: boolean;
  message: string;
  kind: NotificationKind;
  title: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  readonly state = signal<NotificationState>({
    visible: false,
    message: '',
    kind: 'info',
    title: 'Information',
  });

  private hideTimer?: ReturnType<typeof setTimeout>;

  /** durationMs = 0 : popup modal centré, fermeture via OK ou Échap uniquement */
  show(message: string, kind?: NotificationKind, title?: string, durationMs = 0): void {
    const resolvedKind = kind ?? inferKind(message);
    this.state.set({
      visible: true,
      message: message.trim(),
      kind: resolvedKind,
      title: title ?? titleForKind(resolvedKind),
    });
    if (this.hideTimer) {
      clearTimeout(this.hideTimer);
    }
    if (durationMs > 0) {
      this.hideTimer = setTimeout(() => this.dismiss(), durationMs);
    }
  }

  success(message: string, title?: string): void {
    this.show(message, 'success', title ?? 'Succès');
  }

  error(message: string, title?: string): void {
    this.show(message, 'error', title ?? 'Erreur');
  }

  info(message: string, title?: string): void {
    this.show(message, 'info', title ?? 'Information');
  }

  dismiss(): void {
    if (this.hideTimer) {
      clearTimeout(this.hideTimer);
      this.hideTimer = undefined;
    }
    this.state.update((s) => ({ ...s, visible: false }));
  }
}

function inferKind(message: string): NotificationKind {
  const m = message.toLowerCase();
  if (/erreur|impossible|échec|echec|refus|déjà|deja|en attente|⚠/i.test(m)) {
    return 'error';
  }
  if (message.includes('✅') || /succ[eè]s|enregistré|créé|validée?\b/i.test(m)) {
    return 'success';
  }
  return 'info';
}

function titleForKind(kind: NotificationKind): string {
  switch (kind) {
    case 'success':
      return 'Succès';
    case 'error':
      return 'Erreur';
    default:
      return 'Information';
  }
}
