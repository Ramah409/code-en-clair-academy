import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { CaseStudy } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { RewardService } from '../../core/ui/reward.service';
import { IconComponent } from '../../shared/icon.component';
import { MarkdownComponent } from '../../shared/markdown.component';

/**
 * Étude de cas : lecture du contexte, rédaction d'une réponse par tâche (enregistrée), puis corrigé
 * et auto-évaluation avec la grille de critères.
 */
@Component({
  selector: 'app-case-study',
  imports: [FormsModule, RouterLink, IconComponent, MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './case-study.component.html',
  styleUrl: './case-study.component.scss',
})
export class CaseStudyComponent {
  private readonly api = inject(ApiService);
  private readonly rewards = inject(RewardService);

  readonly slug = input.required<string>();

  readonly cas = signal<CaseStudy | null>(null);
  readonly answers = signal<Record<string, string>>({});
  readonly checked = signal<Record<string, number[]>>({});
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);
  readonly savedAt = signal<Date | null>(null);

  readonly answeredCount = computed(() => {
    const c = this.cas();
    if (!c) {
      return 0;
    }
    return c.tasks.filter((_, i) => (this.answers()[String(i + 1)] ?? '').trim().length > 0).length;
  });

  readonly liveScore = computed(() => {
    const c = this.cas();
    if (!c?.revealed) {
      return 0;
    }
    const total = c.tasks.reduce((s, t) => s + (t.criteria?.length ?? 0), 0);
    const ok = Object.values(this.checked()).reduce((s, l) => s + l.length, 0);
    return total ? Math.round((100 * ok) / total) : 0;
  });

  constructor() {
    effect(() => {
      const slug = this.slug();
      untracked(() => this.load(slug));
    });
  }

  private load(slug: string): void {
    this.api.caseStudy(slug).subscribe({
      next: (c) => this.apply(c),
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  private apply(c: CaseStudy): void {
    this.cas.set(c);
    this.answers.set({ ...c.answers });
    this.checked.set(Object.fromEntries(Object.entries(c.checked ?? {}).map(([k, v]) => [k, [...v]])));
  }

  setAnswer(index: number, value: string): void {
    this.answers.update((a) => ({ ...a, [String(index + 1)]: value }));
  }

  save(): void {
    this.saving.set(true);
    this.error.set(null);
    this.api.saveCaseAnswers(this.slug(), this.answers()).subscribe({
      next: (c) => {
        this.saving.set(false);
        this.savedAt.set(new Date());
        this.cas.update((old) => (old ? { ...old, answers: c.answers } : c));
      },
      error: (e: unknown) => {
        this.saving.set(false);
        this.error.set(toFormError(e).message);
      },
    });
  }

  reveal(): void {
    this.saving.set(true);
    this.error.set(null);
    this.api.saveCaseAnswers(this.slug(), this.answers()).subscribe({
      next: () =>
        this.api.revealCase(this.slug()).subscribe({
          next: (c) => {
            this.saving.set(false);
            this.apply(c);
            setTimeout(() => document.getElementById('grille')?.scrollIntoView({ behavior: 'smooth' }));
          },
          error: (e: unknown) => {
            this.saving.set(false);
            this.error.set(toFormError(e).message);
          },
        }),
      error: (e: unknown) => {
        this.saving.set(false);
        this.error.set(toFormError(e).message);
      },
    });
  }

  isChecked(task: number, criterion: number): boolean {
    return (this.checked()[String(task + 1)] ?? []).includes(criterion);
  }

  toggle(task: number, criterion: number): void {
    const key = String(task + 1);
    this.checked.update((c) => {
      const list = c[key] ?? [];
      return { ...c, [key]: list.includes(criterion) ? list.filter((x) => x !== criterion) : [...list, criterion] };
    });
  }

  finish(): void {
    this.saving.set(true);
    this.api.saveCaseChecks(this.slug(), this.checked()).subscribe({
      next: (c) => {
        this.saving.set(false);
        this.apply(c);
        if (c.reward) {
          this.rewards.celebrate(c.reward);
        }
      },
      error: (e: unknown) => {
        this.saving.set(false);
        this.error.set(toFormError(e).message);
      },
    });
  }
}
