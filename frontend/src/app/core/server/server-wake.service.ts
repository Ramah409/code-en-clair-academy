import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';

/** Adresse de santé de l'API (relayée vers /actuator/health par Netlify et par le proxy de développement). */
export const HEALTH_URL = '/api/sante';

/**
 * Vrai quand l'erreur signifie « le serveur ne répond pas encore » : l'hébergement gratuit met l'API
 * en veille et le proxy renvoie 502/503/504 pendant son réveil. Hors ligne, ce n'est pas le cas.
 */
export function isServerUnavailable(err: unknown): boolean {
  if (!(err instanceof HttpErrorResponse)) {
    return false;
  }
  if (isNetworkFailure(err)) {
    return navigator.onLine;
  }
  return err.status === 502 || err.status === 503 || err.status === 504;
}

/**
 * Aucune réponse du réseau. Hors ligne, le service worker d'Angular répond lui-même « 504 » sans
 * aucun en-tête, alors qu'une vraie erreur de proxy (Netlify, Render) en porte toujours.
 */
export function isNetworkFailure(err: HttpErrorResponse): boolean {
  return err.status === 0 || (err.status === 504 && err.headers.keys().length === 0);
}

const pause = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Réveil de l'API.
 *
 * Sur l'offre gratuite de l'hébergeur, l'API s'endort après quelques minutes sans visite et met
 * deux à trois minutes à redémarrer (Java sur 0,1 processeur). Ce service interroge l'adresse de santé jusqu'à ce que
 * l'API réponde, et expose `waking` pour afficher un message d'attente.
 */
@Injectable({ providedIn: 'root' })
export class ServerWakeService {
  private readonly http = inject(HttpClient);
  private pending: Promise<boolean> | null = null;

  private readonly wakingState = signal(false);
  readonly waking = this.wakingState.asReadonly();

  /**
   * Réseau injoignable alors que le navigateur se croit en ligne (Wi-Fi sans Internet, portail de
   * connexion). Redevient faux dès qu'une réponse de l'API arrive.
   */
  private readonly unreachableState = signal(false);
  readonly unreachable = this.unreachableState.asReadonly();

  markReachable(): void {
    if (this.unreachableState()) {
      this.unreachableState.set(false);
    }
  }

  /** Attend que l'API réponde. Renvoie false si l'appareil est hors ligne ou si l'attente dépasse `maxMs`. */
  waitUntilAwake(maxMs = 240_000): Promise<boolean> {
    this.pending ??= this.poll(maxMs).finally(() => {
      this.pending = null;
      this.wakingState.set(false);
    });
    return this.pending;
  }

  private async poll(maxMs: number): Promise<boolean> {
    const start = Date.now();
    let networkFailures = 0;
    while (Date.now() - start < maxMs) {
      if (!navigator.onLine) {
        return false;
      }
      try {
        await firstValueFrom(this.http.get(HEALTH_URL).pipe(timeout(30_000)));
        this.markReachable();
        return true;
      } catch (err) {
        // Trois échecs réseau d'affilée : pas de connexion réelle, inutile d'insister.
        const networkError = err instanceof HttpErrorResponse && isNetworkFailure(err);
        networkFailures = networkError ? networkFailures + 1 : 0;
        if (networkFailures >= 3) {
          this.unreachableState.set(true);
          return false;
        }
        this.wakingState.set(true);
        await pause(3_000);
      }
    }
    return false;
  }
}
