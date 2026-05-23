import {
  Directive,
  effect,
  ElementRef,
  HostListener,
  inject,
  input,
  untracked,
} from '@angular/core';
import { DroitUiService } from '../../core/services/droit-ui.service';

/**
 * Grise et désactive l'élément si l'action catalogue n'est pas autorisée pour le profil courant.
 * Usage : <button appDroitAction="MEMBRE_GERER" (click)="...">
 */
@Directive({
  selector: '[appDroitAction]',
  standalone: true,
})
export class DroitActionDirective {
  private readonly el = inject(ElementRef<HTMLElement>);
  private readonly droitUi = inject(DroitUiService);

  readonly appDroitAction = input.required<string>();

  constructor() {
    effect(() => {
      const code = this.appDroitAction();
      const autorise = this.droitUi.peutFaireAction(code);
      untracked(() => this.appliquerEtat(autorise));
    });
  }

  @HostListener('click', ['$event'])
  bloquerClicSiInterdit(ev: Event): void {
    if (!this.droitUi.peutFaireAction(this.appDroitAction())) {
      ev.preventDefault();
      ev.stopImmediatePropagation();
    }
  }

  private appliquerEtat(autorise: boolean): void {
    const el = this.el.nativeElement;
    if (autorise) {
      el.classList.remove('droit-desactive');
      el.removeAttribute('aria-disabled');
      el.removeAttribute('title');
      if (el.hasAttribute('data-droit-off')) {
        if (this.estInteractifNatif(el)) {
          (el as HTMLButtonElement).disabled = false;
        }
        el.removeAttribute('data-droit-off');
      }
      return;
    }
    el.classList.add('droit-desactive');
    el.setAttribute('aria-disabled', 'true');
    el.setAttribute('title', 'Action non autorisée pour votre profil');
    el.setAttribute('data-droit-off', '1');
    if (this.estInteractifNatif(el)) {
      (el as HTMLButtonElement).disabled = true;
    }
  }

  private estInteractifNatif(el: HTMLElement): el is HTMLButtonElement | HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement {
    const tag = el.tagName;
    return tag === 'BUTTON' || tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA';
  }
}
