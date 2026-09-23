import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { ExerciseView } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { ExercisePanelComponent } from '../../shared/exercise-panel.component';

@Component({
  selector: 'app-exercise-page',
  imports: [RouterLink, ExercisePanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="crumbs" aria-label="Fil d'Ariane">
      <a routerLink="/exercices">Exercices</a>
      @if (exercise()?.context?.courseTitle; as ct) { <span aria-hidden="true">›</span> {{ ct }} }
    </nav>
    @if (exercise(); as e) {
      <div class="exo-page">
        @if (e.context?.lessonSlug) {
          <p class="muted">Exercice de la leçon <a [routerLink]="['/lecon', e.context!.lessonSlug]">{{ e.context!.lessonTitle }}</a>.</p>
        }
        <app-exercise-panel [initial]="e" />
      </div>
    } @else if (error()) {
      <div class="alert alert--error" role="alert">{{ error() }}</div>
    } @else {
      <div class="skeleton skeleton--hero"></div>
    }
  `,
  styles: ['.exo-page { max-width: 960px; margin-top: var(--space-4); }'],
})
export class ExercisePageComponent {
  private readonly api = inject(ApiService);

  readonly slug = input.required<string>();
  readonly exercise = signal<ExerciseView | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const slug = this.slug();
      untracked(() => {
        this.exercise.set(null);
        this.api.exercise(slug).subscribe({
          next: (e) => this.exercise.set(e),
          error: (err: unknown) => this.error.set(toFormError(err).message),
        });
      });
    });
  }
}
