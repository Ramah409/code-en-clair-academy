import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { AuthResponse, User } from './auth.models';
import { AuthService } from './auth.service';

const user: User = {
  id: 1, email: 'a@b.fr', displayName: 'Awa', role: 'USER', xp: 0, level: 1, levelStartXp: 0,
  nextLevelXp: 100, currentStreak: 0, longestStreak: 0, dailyGoalMinutes: 20, theme: 'SYSTEM',
  createdAt: '2026-09-23T00:00:00Z',
};
const session = (token: string): AuthResponse =>
  ({ accessToken: token, tokenType: 'Bearer', expiresAt: '2026-09-23T01:00:00Z', user });

describe('AuthService et authInterceptor', () => {
  let auth: AuthService;
  let http: HttpClient;
  let api: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpClient);
    api = TestBed.inject(HttpTestingController);
  });

  afterEach(() => api.verify());

  it('ouvre la session après la connexion', () => {
    auth.login({ email: 'a@b.fr', password: 'x' }).subscribe();
    api.expectOne('/api/auth/login').flush(session('t1'));

    expect(auth.isAuthenticated()).toBeTrue();
    expect(auth.accessToken).toBe('t1');
  });

  it('partage un seul appel de rafraîchissement entre demandes simultanées', () => {
    auth.refresh().subscribe();
    auth.refresh().subscribe();

    api.expectOne('/api/auth/refresh').flush(session('t2'));
    expect(auth.accessToken).toBe('t2');
  });

  it('restoreSession ne rejette pas sans cookie valide', async () => {
    const restored = auth.restoreSession();
    api.expectOne('/api/auth/refresh').flush({ message: 'Session expirée.' }, { status: 401, statusText: 'Unauthorized' });

    await expectAsync(restored).toBeResolved();
    expect(auth.isAuthenticated()).toBeFalse();
  });

  it('ajoute le jeton aux appels de l\'API, pas aux appels d\'authentification', () => {
    auth.login({ email: 'a@b.fr', password: 'x' }).subscribe();
    const login = api.expectOne('/api/auth/login');
    expect(login.request.headers.has('Authorization')).toBeFalse();
    login.flush(session('t1'));

    http.get('/api/me').subscribe();
    expect(api.expectOne('/api/me').request.headers.get('Authorization')).toBe('Bearer t1');
  });

  it('renouvelle la session puis rejoue la requête sur 401', () => {
    auth.login({ email: 'a@b.fr', password: 'x' }).subscribe();
    api.expectOne('/api/auth/login').flush(session('ancien'));

    let result: unknown;
    http.get('/api/me').subscribe((r) => (result = r));
    api.expectOne('/api/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    api.expectOne('/api/auth/refresh').flush(session('nouveau'));

    const retry = api.expectOne('/api/me');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer nouveau');
    retry.flush(user);
    expect(result).toEqual(user);
  });

  it('ferme la session si le renouvellement échoue', () => {
    auth.login({ email: 'a@b.fr', password: 'x' }).subscribe();
    api.expectOne('/api/auth/login').flush(session('ancien'));

    http.get('/api/me').subscribe({ error: () => undefined });
    api.expectOne('/api/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    api.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBeFalse();
  });
});
