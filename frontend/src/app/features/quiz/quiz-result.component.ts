import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { QuizResult, StartQuiz } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { IconComponent } from '../../shared/icon.component';
import { ProgressRingComponent } from '../../shared/progress-ring.component';
import { QuestionCardComponent } from '../../shared/question-card.component';

@Component({
  selector: 'app-quiz-result',
  imports: [RouterLink, IconComponent, ProgressRingComponent, QuestionCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quiz-result.component.html',
  styleUrl: './quiz-result.component.scss',
})
export class QuizResultComponent {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly id = input.required<string>();
  readonly result = signal<QuizResult | null>(null);
  readonly error = signal<string | null>(null);
  readonly onlyWrong = signal(false);
  readonly restarting = signal(false);

  readonly items = computed(() => (this.result()?.items ?? []).filter((i) => !this.onlyWrong() || !i.correct));
  readonly duration = computed(() => {
    const s = this.result()?.durationSeconds ?? 0;
    return s >= 60 ? `${Math.floor(s / 60)} min ${s % 60} s` : `${s} s`;
  });

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() =>
        this.api.quizResult(id).subscribe({
          next: (r) => this.result.set(r),
          error: (e: unknown) => this.error.set(toFormError(e).message),
        }),
      );
    });
  }

  restart(): void {
    const r = this.result();
    if (!r) {
      return;
    }
    const req: StartQuiz = { scope: r.scope, mode: r.mode, course: r.courseSlug, chapter: r.chapterSlug, count: r.total };
    this.restarting.set(true);
    this.api.startQuiz(req).subscribe({
      next: (a) => void this.router.navigate(['/quiz', a.id]),
      error: (e: unknown) => {
        this.restarting.set(false);
        this.error.set(toFormError(e).message);
      },
    });
  }
}
