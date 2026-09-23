import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService } from '../../core/api/api.service';
import { DIFFICULTY_LABELS, QuizHistoryItem, QuizOptions, QuizStats, StartQuiz } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { IconComponent } from '../../shared/icon.component';
import { MarkdownComponent } from '../../shared/markdown.component';

const SCOPE_LABELS: Record<string, string> = {
  CHAPITRE: 'Chapitre',
  PARCOURS: 'Examen final',
  ENTRAINEMENT: 'Entraînement',
  ERREURS: 'Mes erreurs',
  ALEATOIRE: 'Aléatoire',
  EXAMEN_BLANC: 'Examen blanc',
};

@Component({
  selector: 'app-quiz-hub',
  imports: [FormsModule, RouterLink, SlicePipe, IconComponent, MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quiz-hub.component.html',
  styleUrl: './quiz-hub.component.scss',
})
export class QuizHubComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  /** Paramètres d'URL : ?mode=erreurs ou ?examen=<parcours>. */
  readonly mode = input<string | undefined>(undefined);
  readonly examen = input<string | undefined>(undefined);

  readonly options = signal<QuizOptions | null>(null);
  readonly stats = signal<QuizStats | null>(null);
  readonly history = signal<QuizHistoryItem[]>([]);
  readonly error = signal<string | null>(null);
  readonly starting = signal(false);

  // Configuration de l'entraînement
  readonly course = signal<string>('');
  readonly chapter = signal<string>('');
  readonly difficulty = signal<number | null>(null);
  readonly count = signal(10);
  readonly quizMode = signal<'ENTRAINEMENT' | 'EXAMEN'>('ENTRAINEMENT');
  readonly timed = signal(false);
  readonly minutes = signal(15);

  // Examen final de parcours
  readonly examCount = signal(40);
  readonly examTimed = signal(true);
  readonly examDifficulty = signal<number | null>(null);

  readonly difficulties = DIFFICULTY_LABELS;
  readonly scopeLabels = SCOPE_LABELS;
  readonly counts = [5, 10, 15, 20, 30, 50];
  readonly Math = Math;

  readonly selectedCourse = computed(() => this.options()?.courses.find((c) => c.slug === this.course()));
  readonly examCourse = computed(() => this.options()?.courses.find((c) => c.slug === this.examen()));
  readonly available = computed(() => {
    const c = this.selectedCourse();
    if (!c) {
      return this.options()?.courses.reduce((n, x) => n + x.questionCount, 0) ?? 0;
    }
    return this.chapter() ? (c.chapters.find((ch) => ch.slug === this.chapter())?.questionCount ?? 0) : c.questionCount;
  });

  ngOnInit(): void {
    forkJoin({ options: this.api.quizOptions(), stats: this.api.quizStats(), history: this.api.quizHistory() }).subscribe({
      next: ({ options, stats, history }) => {
        this.options.set(options);
        this.stats.set(stats);
        this.history.set(history.filter((h) => h.submittedAt).slice(0, 12));
      },
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  selectCourse(slug: string): void {
    this.course.set(slug);
    this.chapter.set('');
  }

  startPractice(): void {
    const req: StartQuiz = this.course()
      ? { scope: 'ENTRAINEMENT', course: this.course(), chapter: this.chapter() || undefined }
      : { scope: 'ALEATOIRE' };
    this.start({
      ...req,
      mode: this.quizMode(),
      difficulty: this.difficulty() ?? undefined,
      count: this.count(),
      timeLimitMinutes: this.quizMode() === 'EXAMEN' && this.timed() ? this.minutes() : undefined,
    });
  }

  startMistakes(): void {
    this.start({ scope: 'ERREURS', mode: 'ENTRAINEMENT', count: 15 });
  }

  startRandom(): void {
    this.start({ scope: 'ALEATOIRE', mode: 'ENTRAINEMENT', count: 20 });
  }

  startMock(slug: string): void {
    this.start({ scope: 'EXAMEN_BLANC', exam: slug });
  }

  startCourseExam(): void {
    const course = this.examen();
    if (!course) {
      return;
    }
    this.start({
      scope: 'PARCOURS',
      course,
      count: this.examCount(),
      difficulty: this.examDifficulty() ?? undefined,
      timeLimitMinutes: this.examTimed() ? Math.round(this.examCount() * 1.2) : undefined,
    });
  }

  private start(req: StartQuiz): void {
    this.starting.set(true);
    this.error.set(null);
    this.api.startQuiz(req).subscribe({
      next: (a) => void this.router.navigate(['/quiz', a.id]),
      error: (e: unknown) => {
        this.starting.set(false);
        this.error.set(toFormError(e).message);
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
    });
  }

  rateClass(rate: number): string {
    return rate >= 80 ? 'good' : rate >= 50 ? 'mid' : 'low';
  }
}
