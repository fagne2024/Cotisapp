import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

function orgIdDepuisChemin(chemin: string): number | null {
  const match =
    chemin.match(/\/organisations\/(\d+)/) ?? chemin.match(/\/superadmin\/org\/(\d+)/);
  if (!match) {
    return null;
  }
  const n = Number(match[1]);
  return Number.isNaN(n) ? null : n;
}

export const orgInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  let orgId =
    orgIdDepuisChemin(req.url) ?? orgIdDepuisChemin(router.url) ?? auth.currentOrgId();
  // Membre : une session = une fiche (org du JWT), pas l’org d’une autre URL.
  if (auth.currentRole() === 'MEMBRE' && auth.currentOrgId() != null) {
    orgId = auth.currentOrgId();
  }
  if (orgId != null && req.url.includes('/api/')) {
    req = req.clone({
      setHeaders: { 'X-Organisation-Id': String(orgId) },
    });
  }
  return next(req);
};
