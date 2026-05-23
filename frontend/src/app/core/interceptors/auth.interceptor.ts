import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Endpoints publics : un 401 sur ces URLs ne doit pas déclencher
 * la déconnexion globale (l'identifiant/mot de passe peut simplement être incorrect).
 */
const AUTH_ENDPOINT_SUFFIXES = [
  '/auth/login',
  '/auth/verify-2fa',
  '/auth/comptes-membre',
  '/auth/logout',
];

/** Flag simple pour éviter les déconnexions multiples simultanées */
let isLoggingOut = false;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getToken();

  if (token) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(req).pipe(
    catchError((err: unknown) => {
      const isAuthEndpoint = AUTH_ENDPOINT_SUFFIXES.some((suffix) =>
        req.url.endsWith(suffix) || req.url.includes(suffix + '?')
      );
      if (
        err instanceof HttpErrorResponse &&
        err.status === 401 &&
        !isAuthEndpoint &&
        !isLoggingOut
      ) {
        isLoggingOut = true;
        // clearSession() nettoie le storage ET navigue vers /login
        auth.clearSession();
        // On réinitialise le flag après le cycle de navigation
        setTimeout(() => { isLoggingOut = false; }, 3000);
      }
      return throwError(() => err);
    }),
  );
};
