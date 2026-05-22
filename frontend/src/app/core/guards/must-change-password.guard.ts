import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const mustChangePasswordGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.mustChangePassword()) {
    router.navigate(['/changer-mot-de-passe']);
    return false;
  }
  return true;
};
