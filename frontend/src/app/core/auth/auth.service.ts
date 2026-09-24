import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, tap } from 'rxjs';

import { ServerWakeService, isServerUnavailable } from '../server/server-wake.service';
import { AuthResponse, LoginRequest, RegisterRequest, User } from './auth.models';

/**
 * Session de l'utilisatrice.
 *
 * Le jeton d'accès reste uniquement en mémoire (jamais dans localStorage) ; le jeton de
 * rafraîchissement voyage dans un cookie HttpOnly posé par l'API. Au rechargement de la
 * page, la session est restaurée par un appel à /api/auth/refresh.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly wake = inject(ServerWakeService);

  private readonly token = signal<string | null>(null);
  private readonly currentUser = signal<User | null>(null);

  /** Rafraîchissement en cours, partagé : l'API révoque toutes les sessions si un jeton est réutilisé. */
  private refreshInFlight: Observable<AuthResponse> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  get accessToken(): string | null {
    return this.token();
  }

  login(body: LoginRequest): Observable<User> {
    return this.http.post<AuthResponse>('/api/auth/login', body).pipe(
      tap((res) => this.store(res)),
      map((res) => res.user),
    );
  }

  register(body: RegisterRequest): Observable<User> {
    return this.http.post<AuthResponse>('/api/auth/register', body).pipe(
      tap((res) => this.store(res)),
      map((res) => res.user),
    );
  }

  refresh(): Observable<AuthResponse> {
    this.refreshInFlight ??= this.http.post<AuthResponse>('/api/auth/refresh', null).pipe(
      tap((res) => this.store(res)),
      finalize(() => (this.refreshInFlight = null)),
      shareReplay(1),
    );
    return this.refreshInFlight;
  }

  /**
   * Appelé au démarrage de l'application : ne rejette jamais. Si l'API est en train de se réveiller
   * (hébergement gratuit), attend son réveil puis réessaie, pour ne pas déconnecter l'utilisatrice.
   */
  async restoreSession(): Promise<unknown> {
    try {
      return await firstValueFrom(this.refresh());
    } catch (err) {
      if (isServerUnavailable(err) && (await this.wake.waitUntilAwake())) {
        return firstValueFrom(this.refresh().pipe(catchError(() => of(null))));
      }
      return null;
    }
  }

  /** Recharge le profil depuis l'API (XP, niveau, série à jour). */
  reloadUser(): Observable<User> {
    return this.http.get<User>('/api/me').pipe(tap((user) => this.currentUser.set(user)));
  }

  logout(): void {
    this.http
      .post<void>('/api/auth/logout', null)
      .pipe(catchError(() => of(null)))
      .subscribe(() => this.endSession());
  }

  /** Vide la session locale et renvoie vers la page de connexion. */
  endSession(): void {
    this.clear();
    void this.router.navigate(['/connexion']);
  }

  clear(): void {
    this.token.set(null);
    this.currentUser.set(null);
  }

  private store(res: AuthResponse): void {
    this.token.set(res.accessToken);
    this.currentUser.set(res.user);
  }
}
