import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { JsRunResult } from './js-runner';

/** Affichage de la console d'une exécution JavaScript (sorties et erreurs expliquées). */
@Component({
  selector: 'app-js-console',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="jsc" role="status" aria-live="polite">
      <p class="jsc__title">Console <span>— {{ result().durationMs }} ms</span></p>
      @for (l of result().lines; track $index) {
        <pre class="jsc__line" [class.jsc__line--error]="l.kind === 'error'">{{ l.text }}</pre>
      } @empty {
        <p class="jsc__empty">Rien n'a été affiché. Utilise <code>console.log(...)</code> pour afficher une valeur.</p>
      }
    </div>
  `,
  styles: [
    `
      .jsc { margin-top: var(--space-2); padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); background: var(--code-bg); border: 1px solid var(--border); }
      .jsc__title { margin: 0 0 4px; font-size: var(--fs-xs); font-weight: 700; color: var(--text-muted); }
      .jsc__title span { font-weight: 400; }
      .jsc__line { margin: 0; padding: 2px 0; font-family: var(--font-mono); font-size: var(--fs-sm); white-space: pre-wrap; word-break: break-word; }
      .jsc__line--error { color: var(--rose-700); }
      .jsc__empty { margin: 0; font-size: var(--fs-sm); color: var(--text-muted); }
    `,
  ],
})
export class JsConsoleComponent {
  readonly result = input.required<JsRunResult>();
}
