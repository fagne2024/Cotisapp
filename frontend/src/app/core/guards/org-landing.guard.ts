import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Redirige `/organisations/:id` vers le tableau de bord (tous les profils). */
export const orgLandingGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const orgId = Number(route.parent?.paramMap.get('orgId') ?? route.paramMap.get('orgId'));

  if (
    auth.hasRole(['SUPERADMIN', 'ADMIN_GIE']) ||
    (auth.currentRole() === 'MEMBRE' && auth.currentOrgId() === orgId)
  ) {
    return router.createUrlTree(['/organisations', orgId, 'dashboard']);
  }

  return router.createUrlTree(['/login']);
};
