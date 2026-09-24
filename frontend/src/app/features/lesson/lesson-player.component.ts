import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChildren,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { Answer, Completion, Feedback, LEVEL_LABELS, LessonBlock, LessonView, QuizItem } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { RewardService } from '../../core/ui/reward.service';
import { CodeViewComponent } from '../../shared/code-view.component';
import { ExercisePanelComponent } from '../../shared/exercise-panel.component';
import { IconComponent } from '../../shared/icon.component';
import { MarkdownComponent } from '../../shared/markdown.component';
import { QuestionCardComponent } from '../../shared/question-card.component';
import { SqlRunnerComponent } from '../../shared/sql-runner.component';
import { JsPlaygroundComponent } from '../../shared/js-playground.component';

/** Étape de la leçon : une suite de blocs qui se termine par une interaction (question ou exercice). */
interface Step {
  blocks: LessonBlock[];
  gate?: { type: 'question' | 'exercise' | 'quiz'; refs: string[] };
}

@Component({
  selector: 'app-lesson-player',
  imports: [
    RouterLink,
    CodeViewComponent,
    ExercisePanelComponent,
    IconComponent,
    MarkdownComponent,
    QuestionCardComponent,
    SqlRunnerComponent,
    JsPlaygroundComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lesson-player.component.html',
  styleUrl: './lesson-player.component.scss',
})
export class LessonPlayerComponent {
  private readonly api = inject(ApiService);
  private readonly rewards = inject(RewardService);
  private readonly router = inject(Router);

  readonly slug = input.required<string>();

  readonly lesson = signal<LessonView | null>(null);
  readonly error = signal<string | null>(null);
  readonly revealed = signal(1);
  readonly feedbacks = signal<Record<string, Feedback | null>>({});
  readonly passed = signal<Set<string>>(new Set());
  readonly answering = signal<string | null>(null);
  /** Questions ratées au moins une fois pendant cette visite (pour le score « du premier coup »). */
  readonly missedOnce = signal<Set<string>>(new Set());
  readonly completing = signal(false);
  readonly completion = signal<Completion | null>(null);
  readonly levels = LEVEL_LABELS;

  private readonly stepEls = viewChildren<ElementRef<HTMLElement>>('stepEl');
  private openedAt = Date.now();

  /** Découpage en étapes : chaque question ou exercice ferme une étape. */
  readonly steps = computed<Step[]>(() => {
    const blocks = this.lesson()?.blocks ?? [];
    const steps: Step[] = [];
    let current: LessonBlock[] = [];
    for (const b of blocks) {
      current.push(b);
      if (b.type === 'question' || b.type === 'exercise') {
        steps.push({ blocks: current, gate: { type: b.type, refs: [b.ref] } });
        current = [];
      } else if (b.type === 'quiz') {
        steps.push({ blocks: current, gate: { type: 'quiz', refs: b.items.map((i) => i.ref) } });
        current = [];
      }
    }
    if (current.length) {
      steps.push({ blocks: current });
    }
    return steps;
  });

  readonly visibleSteps = computed(() => this.steps().slice(0, this.revealed()));
  readonly finished = computed(() => this.revealed() >= this.steps().length && this.gateOpen(this.steps().length - 1));
  readonly progress = computed(() => {
    const total = this.steps().length || 1;
    const done = this.finished() ? total : this.revealed() - 1;
    return Math.round((done / total) * 100);
  });
  readonly done = computed(() => this.lesson()?.status === 'TERMINEE' || !!this.completion());

  constructor() {
    effect(() => {
      const slug = this.slug();
      untracked(() => this.load(slug));
    });
  }

  private load(slug: string): void {
    this.lesson.set(null);
    this.completion.set(null);
    this.error.set(null);
    this.feedbacks.set({});
    this.missedOnce.set(new Set());
    this.openedAt = Date.now();
    this.api.lesson(slug).subscribe({
      next: (l) => {
        const passed = new Set<string>();
        for (const b of l.blocks) {
          if (b.type === 'question' && b.answered) {
            passed.add(b.ref);
          }
          if (b.type === 'exercise' && b.exercise?.solved) {
            passed.add(b.ref);
          }
          if (b.type === 'quiz') {
            b.items.filter((i) => i.answered).forEach((i) => passed.add(i.ref));
          }
        }
        this.passed.set(passed);
        this.lesson.set(l);
        const total = this.steps().length;
        this.revealed.set(l.status === 'TERMINEE' ? total : Math.min(Math.max(1, l.currentStep), total));
        window.scrollTo({ top: 0 });
      },
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  gateOpen(index: number): boolean {
    const gate = this.steps()[index]?.gate;
    return !gate || gate.refs.every((r) => this.passed().has(r));
  }

  /** Score du QCM de fin de leçon : questions réussies du premier coup, et questions réussies au total. */
  quizScore(items: QuizItem[]): { firstTry: number; passed: number; total: number } {
    const p = this.passed();
    const missed = this.missedOnce();
    return {
      firstTry: items.filter((i) => p.has(i.ref) && !missed.has(i.ref)).length,
      passed: items.filter((i) => p.has(i.ref)).length,
      total: items.length,
    };
  }

  /**
   * « Revoir le passage » : fait défiler jusqu'au bloc désigné par la question (ou, à défaut, au début
   * de l'étape qui contient la question) et le met en évidence quelques secondes.
   */
  review(anchor: string | undefined, stepIndex: number): void {
    const target = anchor
      ? document.getElementById('passage-' + anchor)
      : this.stepEls()[Math.max(0, stepIndex)]?.nativeElement;
    if (!target) {
      return;
    }
    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    target.classList.add('blk--flash');
    target.setAttribute('tabindex', '-1');
    target.focus({ preventScroll: true });
    setTimeout(() => target.classList.remove('blk--flash'), 2600);
  }

  next(): void {
    const index = this.revealed();
    if (index >= this.steps().length || !this.gateOpen(index - 1)) {
      return;
    }
    this.revealed.set(index + 1);
    this.api.saveLessonStep(this.slug(), index + 1).subscribe({ error: () => undefined });
    // Amène la nouvelle étape à l'écran et place le focus dessus (lecteurs d'écran, clavier)
    setTimeout(() => {
      const el = this.stepEls()[index]?.nativeElement;
      el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      el?.focus({ preventScroll: true });
    });
  }

  answer(code: string, answer: Answer): void {
    this.answering.set(code);
    this.api.answerLessonQuestion(this.slug(), code, answer).subscribe({
      next: (r) => {
        this.answering.set(null);
        this.feedbacks.update((f) => ({ ...f, [code]: r.feedback }));
        if (!r.feedback.correct) {
          this.missedOnce.update((m) => new Set(m).add(code));
        }
        if (r.feedback.correct) {
          this.passed.update((p) => new Set(p).add(code));
          this.rewards.celebrate(r.reward);
        }
      },
      error: (e: unknown) => {
        this.answering.set(null);
        this.rewards.error('Réponse non enregistrée', toFormError(e).message);
      },
    });
  }

  retry(code: string): void {
    this.feedbacks.update((f) => ({ ...f, [code]: null }));
  }

  exerciseSolved(ref: string): void {
    this.passed.update((p) => new Set(p).add(ref));
  }

  complete(): void {
    this.completing.set(true);
    const seconds = Math.round((Date.now() - this.openedAt) / 1000);
    this.api.completeLesson(this.slug(), seconds).subscribe({
      next: (c) => {
        this.completing.set(false);
        this.completion.set(c);
        this.rewards.celebrate(c.reward);
        this.lesson.update((l) => (l ? { ...l, status: 'TERMINEE' } : l));
      },
      error: (e: unknown) => {
        this.completing.set(false);
        this.error.set(toFormError(e).message);
      },
    });
  }

  goQuiz(): void {
    const l = this.lesson();
    if (!l) {
      return;
    }
    const chapter = this.completion()?.chapterSlug ?? l.chapter.slug;
    this.api.startQuiz({ scope: 'CHAPITRE', course: l.course.slug, chapter }).subscribe({
      next: (a) => void this.router.navigate(['/quiz', a.id]),
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  asQuestion(b: LessonBlock) {
    return b as Extract<LessonBlock, { type: 'question' }>;
  }

  asQuiz(b: LessonBlock) {
    return b as Extract<LessonBlock, { type: 'quiz' }>;
  }

  asExercise(b: LessonBlock) {
    return b as Extract<LessonBlock, { type: 'exercise' }>;
  }

  asAny(b: LessonBlock): any {
    return b;
  }
}
