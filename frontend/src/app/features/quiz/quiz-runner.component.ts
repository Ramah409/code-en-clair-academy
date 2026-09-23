import { ChangeDetectionStrategy, Component, OnDestroy, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { Answer, Feedback, QuizAttempt, QuizItem } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { RewardService } from '../../core/ui/reward.service';
import { IconComponent } from '../../shared/icon.component';
import { QuestionCardComponent } from '../../shared/question-card.component';

@Component({
  selector: 'app-quiz-runner',
  imports: [RouterLink, IconComponent, QuestionCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quiz-runner.component.html',
  styleUrl: './quiz-runner.component.scss',
})
export class QuizRunnerComponent implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly rewards = inject(RewardService);

  readonly id = input.required<string>();

  readonly attempt = signal<QuizAttempt | null>(null);
  readonly index = signal(0);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly confirmFinish = signal(false);
  readonly remaining = signal<number | null>(null);
  private timer?: ReturnType<typeof setInterval>;

  readonly training = computed(() => this.attempt()?.mode === 'ENTRAINEMENT');
  readonly item = computed<QuizItem | undefined>(() => this.attempt()?.items[this.index()]);
  readonly answeredCount = computed(() => this.attempt()?.items.filter((i) => i.answered).length ?? 0);
  readonly unanswered = computed(() => (this.attempt()?.questionCount ?? 0) - this.answeredCount());
  readonly correctSoFar = computed(() => this.attempt()?.items.filter((i) => i.feedback?.correct).length ?? 0);
  readonly clock = computed(() => {
    const r = this.remaining();
    if (r === null) {
      return null;
    }
    const m = Math.floor(r / 60);
    const s = r % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  });

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.load(id));
    });
  }

  private load(id: number): void {
    this.api.quizAttempt(id).subscribe({
      next: (a) => {
        if (a.submitted) {
          void this.router.navigate(['/quiz', a.id, 'resultat'], { replaceUrl: true });
          return;
        }
        this.attempt.set(a);
        const firstOpen = a.items.findIndex((i) => !i.answered);
        this.index.set(firstOpen < 0 ? 0 : firstOpen);
        this.startTimer(a.remainingSeconds);
      },
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  private startTimer(seconds?: number): void {
    clearInterval(this.timer);
    if (seconds === undefined || seconds === null) {
      this.remaining.set(null);
      return;
    }
    this.remaining.set(seconds);
    this.timer = setInterval(() => {
      const r = (this.remaining() ?? 0) - 1;
      this.remaining.set(Math.max(0, r));
      if (r <= 0) {
        clearInterval(this.timer);
        this.rewards.info('Temps écoulé', 'Le QCM est corrigé avec les réponses enregistrées.');
        this.finish();
      }
    }, 1000);
  }

  go(i: number): void {
    this.index.set(i);
    this.confirmFinish.set(false);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  answer(answer: Answer): void {
    const a = this.attempt();
    const it = this.item();
    if (!a || !it) {
      return;
    }
    this.busy.set(this.training());
    this.api.answerQuiz(a.id, it.position, answer).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.updateItem(it.position, { answered: true, given: answer, feedback: r.feedback ?? undefined });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.rewards.error('Réponse non enregistrée', toFormError(e).message);
      },
    });
  }

  private updateItem(position: number, patch: Partial<QuizItem>): void {
    this.attempt.update((a) =>
      a ? { ...a, items: a.items.map((i) => (i.position === position ? { ...i, ...patch } : i)) } : a,
    );
  }

  feedbackOf(it: QuizItem): Feedback | null {
    return this.training() ? (it.feedback ?? null) : null;
  }

  next(): void {
    const total = this.attempt()?.items.length ?? 0;
    if (this.index() < total - 1) {
      this.go(this.index() + 1);
    } else {
      this.askFinish();
    }
  }

  askFinish(): void {
    if (this.unanswered() > 0 && !this.confirmFinish()) {
      this.confirmFinish.set(true);
      return;
    }
    this.finish();
  }

  finish(): void {
    const a = this.attempt();
    if (!a || this.busy()) {
      return;
    }
    this.busy.set(true);
    clearInterval(this.timer);
    this.api.submitQuiz(a.id).subscribe({
      next: (res) => {
        this.rewards.celebrate(res.reward);
        (res.newCertificates ?? []).forEach((c) => this.rewards.certificate(c.title));
        void this.router.navigate(['/quiz', a.id, 'resultat'], { replaceUrl: true });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(toFormError(e).message);
      },
    });
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }
}
