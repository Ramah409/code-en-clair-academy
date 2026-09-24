import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { PwaService } from '../core/pwa/pwa.service';
import { ServerWakeService } from '../core/server/server-wake.service';
import { IconComponent } from '../shared/icon.component';

/** Bandeaux d'état de l'application : hors ligne, serveur en cours de réveil, nouvelle version. */
@Component({
  selector: 'app-pwa-banners',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pwa-banners" aria-live="polite">
      @if (!pwa.online() || wake.unreachable()) {
        <p class="pwa-banner pwa-banner--offline" role="status">
          <app-icon name="alert" [size]="18" />
          <span class="pwa-banner__text">Tu es hors ligne. Reconnecte-toi à Internet pour continuer : tes réponses ne peuvent pas être enregistrées.</span>
        </p>
      } @else if (wake.waking()) {
        <p class="pwa-banner pwa-banner--waking" role="status">
          <span class="pwa-banner__spinner" aria-hidden="true"></span>
          <span class="pwa-banner__text">Le serveur se réveille (hébergement gratuit). Cela peut prendre jusqu'à trois minutes…</span>
        </p>
      }
      @if (pwa.updateReady()) {
        <div class="pwa-banner pwa-banner--update" role="status">
          <app-icon name="sparkle" [size]="18" />
          <span class="pwa-banner__text">Une nouvelle version de l'application est disponible.</span>
          <button type="button" class="btn btn--secondary pwa-banner__action" (click)="pwa.applyUpdate()">
            Mettre à jour
          </button>
        </div>
      }
    </div>
  `,
})
export class PwaBannersComponent {
  readonly pwa = inject(PwaService);
  readonly wake = inject(ServerWakeService);
}
