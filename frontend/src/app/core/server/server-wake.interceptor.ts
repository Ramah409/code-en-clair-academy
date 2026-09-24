import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, tap, throwError } from 'rxjs';

import { HEALTH_URL, ServerWakeService, isServerUnavailable } from './server-wake.service';

/**
 * Si l'API dort (hébergement gratuit), attend son réveil puis rejoue une seule fois les lectures (GET).
 * Les envois (POST, PUT, DELETE) ne sont jamais rejoués : ils ont pu être traités malgré l'erreur du
 * proxy, et les rejouer créerait des doublons. Leur erreur remonte à l'écran ; le réveil démarre quand même.
 */
export const serverWakeInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || req.url === HEALTH_URL) {
    return next(req);
  }

  const wake = inject(ServerWakeService);
  return next(req).pipe(
    tap((event) => {
      if (event instanceof HttpResponse) {
        wake.markReachable();
      }
    }),
    catchError((err: unknown) => {
      if (!isServerUnavailable(err)) {
        return throwError(() => err);
      }
      if (req.method !== 'GET') {
        void wake.waitUntilAwake();
        return throwError(() => err);
      }
      return from(wake.waitUntilAwake()).pipe(
        switchMap((awake) => (awake ? next(req) : throwError(() => err))),
      );
    }),
  );
};
