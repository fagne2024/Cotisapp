import { HttpErrorResponse, HttpInterceptorFn, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { BehaviorSubject, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

const AUTH_ENDPOINT_SUFFIXES = [
  '/auth/login',
  '/auth/verify-2fa',
  '/auth/comptes-membre',
  '/auth/logout',
  '/auth/refresh',
];

let isRefreshing = false;
const refreshDone$ = new BehaviorSubject<boolean>(false);

function isAuthEndpoint(url: string): boolean {
  return AUTH_ENDPOINT_SUFFIXES.some(
    (s) => url.endsWith(s) || url.includes(s + '?'),
  );
}

function addBearer(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getToken();

  if (token) {
    req = addBearer(req, token);
  }

  return next(req).pipe(
    catchError((err: unknown) => {
      if (
        err instanceof HttpErrorResponse &&
        err.status === 401 &&
        !isAuthEndpoint(req.url)
      ) {
        return handle401(req, next, auth);
      }
      return throwError(() => err);
    }),
  );
};

function handle401(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  auth: AuthService,
) {
  if (!auth.getRefreshToken()) {
    auth.clearSession();
    return throwError(() => new Error('Session expirée'));
  }

  if (isRefreshing) {
    // Une autre requête est déjà en train de rafraîchir — on attend le résultat
    return refreshDone$.pipe(
      filter((done) => done),
      take(1),
      switchMap(() => {
        const newToken = auth.getToken();
        return newToken ? next(addBearer(req, newToken)) : throwError(() => new Error('Session expirée'));
      }),
    );
  }

  isRefreshing = true;
  refreshDone$.next(false);

  return auth.refresh().pipe(
    switchMap(() => {
      isRefreshing = false;
      refreshDone$.next(true);
      const newToken = auth.getToken()!;
      return next(addBearer(req, newToken));
    }),
    catchError((refreshErr) => {
      isRefreshing = false;
      refreshDone$.next(false);
      auth.clearSession();
      return throwError(() => refreshErr);
    }),
  );
}
