import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { DroitAccesService } from '../services/droit-acces.service';
import { landingBureau } from '../util/landing-route.util';

/** Redirige `/organisations/:id` vers la première page autorisée selon le rôle. */
export const orgLandingGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const droits = inject(DroitAccesService);
  const router = inject(Router);
  const orgId = Number(route.parent?.paramMap.get('orgId') ?? route.paramMap.get('orgId'));

  if (auth.hasRole(['SUPERADMIN', 'ADMIN_GIE'])) {
    return router.createUrlTree(['/organisations', orgId, 'dashboard']);
  }

  if (auth.currentRole() === 'MEMBRE' && auth.currentOrgId() === orgId) {
    if (auth.currentMembreId() != null) {
      return router.createUrlTree(['/organisations', orgId, 'mon-compte']);
    }
    const cached = droits.droits();
    if (cached) {
      return router.createUrlTree(landingBureau(orgId, cached));
    }
    return droits.chargerEtMemoriser(orgId).pipe(
      map((d) => {
        droits.setDroits(d);
        return router.createUrlTree(landingBureau(orgId, d));
      }),
      catchError(() => of(router.createUrlTree(['/organisations', orgId, 'mon-profil'])))
    );
  }

  return router.createUrlTree(['/login']);
};
