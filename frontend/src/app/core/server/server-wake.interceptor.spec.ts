import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { serverWakeInterceptor } from './server-wake.interceptor';
import { HEALTH_URL, ServerWakeService } from './server-wake.service';

describe('serverWakeInterceptor', () => {
  let http: HttpClient;
  let api: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([serverWakeInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    api = TestBed.inject(HttpTestingController);
  });

  afterEach(() => api.verify());

  /** Laisse les promesses du service de réveil avancer entre deux réponses simulées. */
  const flushMicrotasks = () => new Promise((resolve) => setTimeout(resolve));

  it('rejoue une lecture une fois le serveur réveillé', async () => {
    let body: unknown;
    http.get('/api/courses').subscribe((b) => (body = b));

    api.expectOne('/api/courses').flush('Réveil', { status: 503, statusText: 'Service Unavailable' });
    await flushMicrotasks();
    api.expectOne(HEALTH_URL).flush({ status: 'UP' });
    await flushMicrotasks();
    api.expectOne('/api/courses').flush([{ slug: 'java' }]);

    expect(body).toEqual([{ slug: 'java' }]);
  });

  it('ne rejoue jamais un envoi (POST), pour éviter les doublons', async () => {
    let status = 0;
    http.post('/api/exercises/x/submit', {}).subscribe({ error: (e) => (status = e.status) });

    api.expectOne('/api/exercises/x/submit').flush('', { status: 504, statusText: 'Gateway Timeout' });
    expect(status).toBe(504);

    // Le réveil démarre quand même en arrière-plan, sans rejouer l'envoi.
    await flushMicrotasks();
    api.expectOne(HEALTH_URL).flush({ status: 'UP' });
    await flushMicrotasks();
    api.expectNone('/api/exercises/x/submit');
  });

  it('laisse passer les autres erreurs sans attendre', () => {
    let status = 0;
    http.get('/api/courses/inconnu').subscribe({ error: (e) => (status = e.status) });
    api.expectOne('/api/courses/inconnu').flush('', { status: 404, statusText: 'Not Found' });
    expect(status).toBe(404);
    expect(TestBed.inject(ServerWakeService).waking()).toBeFalse();
  });
});
