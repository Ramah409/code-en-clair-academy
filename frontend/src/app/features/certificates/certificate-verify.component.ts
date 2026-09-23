import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { PublicCertificate } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { IconComponent } from '../../shared/icon.component';

/** Page publique : vérifier qu'une attestation existe à partir de son code (sans connexion). */
@Component({
  selector: 'app-certificate-verify',
  imports: [DatePipe, FormsModule, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="cv" id="contenu">
      <p class="cv__brand">Code en Clair<span>Academy</span></p>
      <h1>Vérifier une attestation</h1>
      <form class="cv__form" (ngSubmit)="go()">
        <label for="cv-code">Code de l'attestation</label>
        <div class="cv__row">
          <input id="cv-code" name="code" class="input" placeholder="CEC-XXXX-XXXX" autocomplete="off"
                 [ngModel]="typed()" (ngModelChange)="typed.set($event)" />
          <button type="submit" class="btn">Vérifier</button>
        </div>
      </form>
      @if (result(); as r) {
        <section class="cv__ok" role="status">
          <app-icon name="check" [size]="28" />
          <div>
            <p class="cv__title">{{ r.title }}</p>
            <p>Délivrée à <strong>{{ r.holderName }}</strong> le {{ r.issuedAt | date: 'd MMMM y' }}.</p>
            <p class="cv__muted">Cette attestation est authentique : elle a été délivrée par Code en Clair Academy sur des résultats réellement obtenus.</p>
          </div>
        </section>
      } @else if (error()) {
        <p class="cv__ko" role="alert"><app-icon name="x" [size]="20" /> {{ error() }}</p>
      }
      <p class="cv__back"><a routerLink="/connexion">Aller sur Code en Clair Academy</a></p>
    </main>
  `,
  styles: [
    `
      .cv { max-width: 640px; margin: 0 auto; padding: var(--space-8) var(--space-4); }
      .cv__brand { font-family: var(--font-display); font-size: 1.3rem; font-weight: 600; color: var(--primary); }
      .cv__brand span { margin-left: 0.3em; font-style: italic; font-weight: 400; color: var(--rose-500); }
      .cv__form label { display: block; margin-bottom: var(--space-1); font-weight: 700; }
      .cv__row { display: flex; gap: var(--space-2); flex-wrap: wrap; }
      .cv__row .input { flex: 1 1 220px; font-family: var(--font-mono); text-transform: uppercase; }
      .cv__ok { display: flex; gap: var(--space-3); margin-top: var(--space-5); padding: var(--space-4); border-radius: var(--radius-lg); background: var(--primary-soft); color: var(--primary); }
      .cv__ok p { margin: 0 0 var(--space-1); color: var(--text); }
      .cv__title { font-weight: 700; font-size: var(--fs-lg); }
      .cv__muted { font-size: var(--fs-sm); color: var(--text-muted) !important; }
      .cv__ko { display: flex; align-items: center; gap: var(--space-2); margin-top: var(--space-5); color: var(--rose-700); font-weight: 600; }
      .cv__back { margin-top: var(--space-6); }
    `,
  ],
})
export class CertificateVerifyComponent {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly code = input<string | undefined>(undefined);
  readonly typed = signal('');
  readonly result = signal<PublicCertificate | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const code = this.code();
      untracked(() => {
        this.typed.set(code ?? '');
        this.result.set(null);
        this.error.set(null);
        if (code) {
          this.api.verifyCertificate(code).subscribe({
            next: (r) => this.result.set(r),
            error: (e: unknown) => this.error.set(toFormError(e).message),
          });
        }
      });
    });
  }

  go(): void {
    const code = this.typed().trim().toUpperCase();
    if (code) {
      void this.router.navigate(['/verification', code]);
    }
  }
}
