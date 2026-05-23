import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { DroitAccesService } from '../services/droit-acces.service';

export const orgGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const droits = inject(DroitAccesService);
  const router = inject(Router);
  const orgId = Number(route.paramMap.get('orgId'));
  if (auth.currentRole() === 'SUPERADMIN') return true;
  if (auth.currentOrgId() === orgId) {
    if (auth.currentRole() === 'MEMBRE' && auth.compteBureau() && !droits.droits()) {
      return droits.chargerEtMemoriser(orgId).pipe(
        tap((d) => droits.setDroits(d)),
        map(() => true),
        catchError(() => {
          router.navigate(['/403']);
          return of(false);
        })
      );
    }
    return true;
  }
  router.navigate(['/403']);
  return false;
};
