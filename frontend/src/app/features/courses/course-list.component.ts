import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { CourseSummary, LEVEL_LABELS, Level } from '../../core/api/models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-course-list',
  imports: [RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <h1>Parcours</h1>
      <p class="lead">Chaque parcours va du niveau débutant à la préparation à l'examen CDA : leçons courtes,
        exercices corrigés, QCM de fin de chapitre, examen final et projet.</p>
    </header>

    <div class="filters" role="group" aria-label="Filtrer par niveau">
      <button type="button" class="chip-toggle" [class.chip-toggle--on]="!level()" (click)="level.set(null)">Tous</button>
      @for (l of levelKeys; track l) {
        <button type="button" class="chip-toggle" [class.chip-toggle--on]="level() === l" (click)="level.set(l)">{{ levels[l] }}</button>
      }
    </div>

    @if (courses(); as list) {
      <ul class="tracks">
        @for (c of filtered(); track c.slug) {
          <li>
            <a class="track" [routerLink]="['/parcours', c.slug]">
              <span class="track__icon"><app-icon [name]="c.icon" [size]="26" /></span>
              <span class="track__body">
                <span class="track__title">{{ c.title }}</span>
                <span class="track__summary">{{ c.summary }}</span>
                <span class="track__levels">
                  @for (l of c.levels; track l) {
                    <span class="tag">{{ levels[l] }}</span>
                  }
                </span>
                <span class="track__stats">
                  <span><app-icon name="book" [size]="15" /> {{ c.chapterCount }} chapitres, {{ c.lessonCount }} leçons</span>
                  <span><app-icon name="code" [size]="15" /> {{ c.exerciseCount }} exercices</span>
                  <span><app-icon name="quiz" [size]="15" /> {{ c.questionCount }} questions</span>
                </span>
              </span>
              <span class="track__progress">
                @if (c.examPassed) {
                  <span class="tag tag--done"><app-icon name="check" [size]="14" /> Validé</span>
                } @else if (c.lessonsDone > 0) {
                  <span class="track__pct">{{ c.percent }} %</span>
                } @else {
                  <span class="track__start">Commencer</span>
                }
                <span class="meter"><span [style.width.%]="c.percent"></span></span>
              </span>
            </a>
          </li>
        } @empty {
          <li class="empty">Aucun parcours à ce niveau pour le moment.</li>
        }
      </ul>
    } @else {
      <div class="skeleton skeleton--list"></div>
    }
  `,
})
export class CourseListComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly courses = signal<CourseSummary[] | null>(null);
  readonly level = signal<Level | null>(null);
  readonly levels = LEVEL_LABELS;
  readonly levelKeys = Object.keys(LEVEL_LABELS) as Level[];

  readonly filtered = computed(() => {
    const l = this.level();
    return (this.courses() ?? []).filter((c) => !l || c.levels.includes(l));
  });

  ngOnInit(): void {
    this.api.courses().subscribe((c) => this.courses.set(c));
  }
}
