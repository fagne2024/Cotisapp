import { Injectable, inject } from '@angular/core';
import { AuthService } from './auth.service';
import { DroitAccesService } from './droit-acces.service';

/**
 * Vérifie si l'utilisateur courant peut exécuter une action catalogue (boutons, liens…).
 * Admin / superadmin : toujours oui. Membre de bureau : selon /mes-droits.
 */
@Injectable({ providedIn: 'root' })
export class DroitUiService {
  private readonly auth = inject(AuthService);
  private readonly droits = inject(DroitAccesService);

  peutFaireAction(code: string): boolean {
    if (!code?.trim()) {
      return true;
    }
    if (this.auth.hasRole(['SUPERADMIN', 'ADMIN_GIE'])) {
      return true;
    }
    if (!this.auth.compteBureau()) {
      return true;
    }
    const orgId = this.auth.currentOrgId();
    if (orgId == null || this.droits.droits() == null) {
      return false;
    }
    return this.droits.peutAction(orgId, code);
  }
}
