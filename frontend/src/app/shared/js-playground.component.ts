import { ChangeDetectionStrategy, Component, OnInit, input, signal } from '@angular/core';

import { CodeEditorComponent } from './code-editor.component';
import { IconComponent } from './icon.component';
import { JsConsoleComponent } from './js-console.component';
import { JsRunResult, runJavaScript } from './js-runner';

/** Démonstration JavaScript modifiable et exécutable dans le navigateur. */
@Component({
  selector: 'app-js-playground',
  imports: [CodeEditorComponent, IconComponent, JsConsoleComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-code-editor language="javascript" [value]="initial()" [minHeight]="110" ariaLabel="Code JavaScript à exécuter"
                     (valueChange)="code = $event" (run)="run()" />
    <div class="row-actions" style="margin-top: 8px">
      <button type="button" class="btn btn--secondary btn--sm" (click)="run()" [disabled]="running()">
        <app-icon name="play" [size]="16" /> Exécuter
      </button>
      <span class="muted" style="font-size: var(--fs-xs)">Ton code tourne dans ton navigateur, isolé du reste de la page.</span>
    </div>
    @if (result(); as r) { <app-js-console [result]="r" /> }
  `,
})
export class JsPlaygroundComponent implements OnInit {
  readonly initial = input.required<string>();
  readonly result = signal<JsRunResult | null>(null);
  readonly running = signal(false);
  code = '';

  ngOnInit(): void {
    this.code = this.initial();
  }

  async run(): Promise<void> {
    this.running.set(true);
    this.result.set(await runJavaScript(this.code));
    this.running.set(false);
  }
}
