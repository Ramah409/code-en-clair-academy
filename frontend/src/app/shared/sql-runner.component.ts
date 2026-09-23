import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';

import { toFormError } from '../core/auth/api-error';
import { ApiService } from '../core/api/api.service';
import { LabRun } from '../core/api/models';
import { LabSchemaStore } from '../core/ui/lab-schema.store';
import { CodeEditorComponent } from './code-editor.component';
import { IconComponent } from './icon.component';
import { ResultTableComponent } from './result-table.component';

/**
 * Éditeur SQL relié au laboratoire : exécution réelle sur la base pédagogique
 * (transaction annulée), affichage des résultats de chaque instruction et des erreurs expliquées.
 */
@Component({
  selector: 'app-sql-runner',
  imports: [CodeEditorComponent, IconComponent, ResultTableComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="runner">
      <app-code-editor language="sql" [value]="code()" [minHeight]="minHeight()" [schema]="schema.completion()"
                       ariaLabel="Éditeur SQL" (valueChange)="onChange($event)" (run)="execute()" />
      <div class="runner__bar">
        <button type="button" class="btn btn--sm" (click)="execute()" [disabled]="running()">
          <app-icon name="play" [size]="16" /> {{ running() ? 'Exécution…' : 'Exécuter' }}
        </button>
        <span class="runner__kbd">Ctrl / ⌘ + Entrée</span>
        @if (dirty()) {
          <button type="button" class="btn btn--ghost btn--sm" (click)="reset()">
            <app-icon name="refresh" [size]="16" /> Revenir au code d'origine
          </button>
        }
      </div>

      @if (error()) {
        <div class="alert alert--error" role="alert">{{ error() }}</div>
      }
      @if (run(); as r) {
        <div class="runner__results" aria-live="polite">
          @for (res of r.results; track $index) {
            @if (res.data) {
              <app-result-table [data]="res.data" />
            } @else if (r.results.length === 1 || $last) {
              <p class="runner__count">
                <app-icon name="check" [size]="16" />
                {{ res.command }} : {{ res.updateCount ?? 0 }} ligne(s) concernée(s)
              </p>
            }
          }
          @if (r.error; as e) {
            <div class="sql-error" role="alert">
              <p class="sql-error__title"><app-icon name="alert" [size]="18" /> Erreur PostgreSQL</p>
              <p class="sql-error__msg">{{ e.message }}</p>
              @if (e.hint) {
                <p class="sql-error__hint"><app-icon name="lightbulb" [size]="16" /> {{ e.hint }}</p>
              }
            </div>
          }
          @if (!r.error && hasWrites(r)) {
            <p class="runner__note">Les modifications sont annulées après l'exécution : la base pédagogique reste intacte.</p>
          }
        </div>
      }
    </div>
  `,
})
export class SqlRunnerComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly schema = inject(LabSchemaStore);

  readonly initial = input<string>('');
  readonly minHeight = input(110);
  readonly executed = output<LabRun>();

  readonly code = signal('');
  readonly running = signal(false);
  readonly run = signal<LabRun | null>(null);
  readonly error = signal<string | null>(null);
  readonly dirty = signal(false);
  private currentCode = '';

  ngOnInit(): void {
    this.code.set(this.initial());
    this.currentCode = this.initial();
    this.schema.load();
  }

  onChange(value: string): void {
    this.currentCode = value;
    this.dirty.set(value !== this.initial());
  }

  reset(): void {
    this.code.set(this.initial() + ' ');
    queueMicrotask(() => this.code.set(this.initial()));
    this.currentCode = this.initial();
    this.dirty.set(false);
  }

  execute(): void {
    if (this.running()) {
      return;
    }
    this.running.set(true);
    this.error.set(null);
    this.api.runSql(this.currentCode).subscribe({
      next: (r) => {
        this.run.set(r);
        this.running.set(false);
        this.executed.emit(r);
      },
      error: (err: unknown) => {
        this.error.set(toFormError(err).message);
        this.run.set(null);
        this.running.set(false);
      },
    });
  }

  hasWrites(r: LabRun): boolean {
    return r.results.some((x) => x.updateCount !== undefined && x.command !== 'SELECT');
  }
}
