import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { RewardService } from '../core/ui/reward.service';
import { IconComponent } from '../shared/icon.component';

/** Pile de notifications (XP gagnée, niveau, badge, messages). */
@Component({
  selector: 'app-toasts',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toasts" aria-live="polite" aria-atomic="false">
      @for (t of rewards.toasts(); track t.id) {
        <div class="toast toast--{{ t.kind }}" role="status">
          <span class="toast__icon"><app-icon [name]="t.icon" [size]="20" /></span>
          <span class="toast__text">
            <strong>{{ t.title }}</strong>
            @if (t.detail) {
              <span>{{ t.detail }}</span>
            }
          </span>
          <button type="button" class="toast__close" (click)="rewards.dismiss(t.id)" aria-label="Fermer la notification">
            <app-icon name="x" [size]="16" />
          </button>
        </div>
      }
    </div>
  `,
})
export class ToastsComponent {
  readonly rewards = inject(RewardService);
}
