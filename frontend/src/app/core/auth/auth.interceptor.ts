import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';

const withToken = (req: HttpRequest<unknown>, token: string | null) =>
  token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

/**
 * Ajoute le jeton d'accès aux appels de l'API. Si l'API répond 401 (jeton expiré),
 * renouvelle la session une seule fois puis rejoue la requête.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || req.url.startsWith('/api/auth/')) {
    return next(req);
  }

  const auth = inject(AuthService);
  return next(withToken(req, auth.accessToken)).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401 || !auth.accessToken) {
        return throwError(() => err);
      }
      return auth.refresh().pipe(
        catchError((refreshErr: unknown) => {
          auth.endSession();
          return throwError(() => refreshErr);
        }),
        switchMap((res) => next(withToken(req, res.accessToken))),
      );
    }),
  );
};
