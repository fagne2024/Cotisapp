import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const mustSetupTwoFactorGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.mustSetupTwoFactor()) {
    router.navigate(['/configurer-2fa']);
    return false;
  }
  return true;
};
