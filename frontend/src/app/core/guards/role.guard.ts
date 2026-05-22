import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { DroitAccesService } from '../services/droit-acces.service';
import { Role } from '../models/role.model';

/**
 * Garde par rôle Spring + droits applicatifs pour les membres de bureau (MEMBRE).
 * - `roles` : SUPERADMIN / ADMIN_GIE / MEMBRE (classique)
 * - `action` : code catalogue requis (ex. OP_COTISATION)
 * - `gestionBureau` : accès si peutGestion (menu gestion)
 * - `sansFicheMembre` : mon-profil etc. sans fiche membre liée
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const droits = inject(DroitAccesService);
  const router = inject(Router);
  const required = (route.data['roles'] as Role[]) ?? [];
  const action = route.data['action'] as string | undefined;
  const gestionBureau = route.data['gestionBureau'] === true;
  const sansFicheMembre = route.data['sansFicheMembre'] === true;
  const orgId = auth.currentOrgId();

  if (auth.hasRole(['SUPERADMIN', 'ADMIN_GIE'])) {
    if (!required.length || auth.hasRole(required)) {
      return true;
    }
  }

  if (auth.currentRole() === 'MEMBRE' && orgId != null) {
    if (sansFicheMembre && required.includes('MEMBRE')) {
      return true;
    }
    if (auth.currentMembreId() != null && required.includes('MEMBRE') && auth.hasRole(['MEMBRE'])) {
      return true;
    }
    if (action && droits.peutAction(orgId, action)) {
      return true;
    }
    if (gestionBureau && droits.peutGestion(orgId)) {
      return true;
    }
  }

  if (required.length && auth.hasRole(required)) {
    return true;
  }

  router.navigate(['/403']);
  return false;
};
