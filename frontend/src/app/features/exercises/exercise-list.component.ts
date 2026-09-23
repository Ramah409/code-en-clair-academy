import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { CatalogItem, DIFFICULTY_NAMES, EXERCISE_KIND_LABELS } from '../../core/api/models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-exercise-list',
  imports: [FormsModule, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <h1>Exercices</h1>
      <p class="lead">Tous les exercices pratiques des parcours, corrigés automatiquement. Chaque exercice propose trois
        indices graduels et une correction expliquée.</p>
    </header>

    <div class="toolbar">
      <label class="toolbar__field">
        <span>Parcours</span>
        <select class="select" [ngModel]="course()" (ngModelChange)="course.set($event)">
          <option value="">Tous</option>
          @for (c of courses(); track c.slug) { <option [value]="c.slug">{{ c.title }}</option> }
        </select>
      </label>
      <label class="toolbar__field">
        <span>Type</span>
        <select class="select" [ngModel]="kind()" (ngModelChange)="kind.set($event)">
          <option value="">Tous</option>
          @for (k of kinds(); track k) { <option [value]="k">{{ kindLabels[k] }}</option> }
        </select>
      </label>
      <label class="toolbar__field">
        <span>Difficulté</span>
        <select class="select" [ngModel]="difficulty()" (ngModelChange)="difficulty.set($event)">
          <option value="">Toutes</option>
          @for (d of ['FACILE', 'INTERMEDIAIRE', 'DIFFICILE']; track d) { <option [value]="d">{{ difficultyNames[d] }}</option> }
        </select>
      </label>
      <label class="toolbar__field">
        <span>État</span>
        <select class="select" [ngModel]="status()" (ngModelChange)="status.set($event)">
          <option value="">Tous</option>
          <option value="todo">À faire</option>
          <option value="attempted">Commencés</option>
          <option value="solved">Réussis</option>
        </select>
      </label>
    </div>

    @if (items(); as list) {
      <p class="muted">{{ filtered().length }} exercice{{ filtered().length > 1 ? 's' : '' }} — {{ solvedCount() }} réussi{{ solvedCount() > 1 ? 's' : '' }} au total.</p>
      <ul class="exo-list">
        @for (e of filtered(); track e.slug) {
          <li>
            <a class="exo-row" [routerLink]="['/exercices', e.slug]" [class.exo-row--done]="e.solved">
              <span class="exo-row__state" aria-hidden="true">
                <app-icon [name]="e.solved ? 'check' : e.attempted ? 'edit' : 'code'" [size]="18" />
              </span>
              <span class="exo-row__main">
                <span class="exo-row__title">{{ e.title }}</span>
                <span class="exo-row__meta">{{ e.courseTitle }}@if (e.lessonTitle) { — {{ e.lessonTitle }} }</span>
              </span>
              <span class="tag tag--kind">{{ kindLabels[e.kind] }}</span>
              <span class="tag">{{ difficultyNames[e.difficulty] }}</span>
              <span class="exo-row__xp">{{ e.xp }} XP</span>
            </a>
          </li>
        } @empty {
          <li class="empty">Aucun exercice ne correspond à ces filtres.</li>
        }
      </ul>
    } @else {
      <div class="skeleton skeleton--list"></div>
    }
  `,
})
export class ExerciseListComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly items = signal<CatalogItem[] | null>(null);
  readonly course = signal('');
  readonly kind = signal('');
  readonly difficulty = signal('');
  readonly status = signal('');
  readonly kindLabels = EXERCISE_KIND_LABELS;
  readonly difficultyNames = DIFFICULTY_NAMES;

  readonly courses = computed(() => {
    const seen = new Map<string, string>();
    for (const e of this.items() ?? []) {
      if (e.courseSlug && e.courseTitle) {
        seen.set(e.courseSlug, e.courseTitle);
      }
    }
    return [...seen].map(([slug, title]) => ({ slug, title }));
  });
  readonly kinds = computed(() => [...new Set((this.items() ?? []).map((e) => e.kind))]);
  readonly solvedCount = computed(() => (this.items() ?? []).filter((e) => e.solved).length);
  readonly filtered = computed(() =>
    (this.items() ?? []).filter(
      (e) =>
        (!this.course() || e.courseSlug === this.course()) &&
        (!this.kind() || e.kind === this.kind()) &&
        (!this.difficulty() || e.difficulty === this.difficulty()) &&
        (!this.status() ||
          (this.status() === 'solved' && e.solved) ||
          (this.status() === 'attempted' && e.attempted && !e.solved) ||
          (this.status() === 'todo' && !e.attempted)),
    ),
  );

  ngOnInit(): void {
    this.api.exercises().subscribe((list) => this.items.set(list));
  }
}
