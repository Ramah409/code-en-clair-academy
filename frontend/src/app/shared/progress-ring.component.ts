import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Anneau de progression (objectif du jour, score). */
@Component({
  selector: 'app-progress-ring',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 100 100" role="img" [attr.aria-label]="label()">
      <circle cx="50" cy="50" r="42" fill="none" stroke="var(--bg-sunken)" stroke-width="9" />
      <circle cx="50" cy="50" r="42" fill="none" [attr.stroke]="color()" stroke-width="9" stroke-linecap="round"
              [attr.stroke-dasharray]="circumference" [attr.stroke-dashoffset]="offset()"
              transform="rotate(-90 50 50)" class="ring__arc" />
    </svg>
    <div class="ring__center"><ng-content /></div>
  `,
  styles: [
    `
      :host { position: relative; display: inline-grid; place-items: center; }
      .ring__center { position: absolute; inset: 0; display: grid; place-items: center; text-align: center; }
      .ring__arc { transition: stroke-dashoffset 600ms ease; }
    `,
  ],
})
export class ProgressRingComponent {
  readonly percent = input(0);
  readonly size = input(120);
  readonly color = input('var(--primary)');
  readonly label = input('Progression');

  readonly circumference = 2 * Math.PI * 42;
  readonly offset = computed(() => this.circumference * (1 - Math.min(100, Math.max(0, this.percent())) / 100));
}
