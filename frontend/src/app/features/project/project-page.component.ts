import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { DIFFICULTY_NAMES, ProjectStep, ProjectView } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { RewardService } from '../../core/ui/reward.service';
import { IconComponent } from '../../shared/icon.component';
import { MarkdownComponent } from '../../shared/markdown.component';

@Component({
  selector: 'app-project-page',
  imports: [RouterLink, IconComponent, MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './project-page.component.html',
  styleUrl: './project-page.component.scss',
})
export class ProjectPageComponent {
  private readonly api = inject(ApiService);
  private readonly rewards = inject(RewardService);

  readonly slug = input.required<string>();
  readonly project = signal<ProjectView | null>(null);
  readonly checked = signal<Record<number, Set<number>>>({});
  readonly error = signal<string | null>(null);
  readonly busy = signal<number | null>(null);
  readonly difficulties = DIFFICULTY_NAMES;

  constructor() {
    effect(() => {
      const slug = this.slug();
      untracked(() => this.load(slug));
    });
  }

  private load(slug: string): void {
    this.api.project(slug).subscribe({
      next: (p) => this.project.set(p),
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  isChecked(step: ProjectStep, i: number): boolean {
    return step.done || (this.checked()[step.position]?.has(i) ?? false);
  }

  toggle(step: ProjectStep, i: number): void {
    this.checked.update((c) => {
      const set = new Set(c[step.position] ?? []);
      if (set.has(i)) {
        set.delete(i);
      } else {
        set.add(i);
      }
      return { ...c, [step.position]: set };
    });
  }

  ready(step: ProjectStep): boolean {
    const set = this.checked()[step.position] ?? new Set<number>();
    return step.checklist.every((_, i) => set.has(i)) && (!step.exercise || step.exercise.solved);
  }

  current(p: ProjectView): number {
    return p.steps.find((s) => !s.done)?.position ?? -1;
  }

  complete(step: ProjectStep): void {
    this.busy.set(step.position);
    this.error.set(null);
    this.api.completeProjectStep(this.slug(), step.position, [...(this.checked()[step.position] ?? [])]).subscribe({
      next: (r) => {
        this.busy.set(null);
        this.rewards.celebrate(r.reward);
        if (r.projectCompleted) {
          this.rewards.info('Projet terminé', 'La correction complète est maintenant disponible.');
        }
        this.load(this.slug());
      },
      error: (e: unknown) => {
        this.busy.set(null);
        this.error.set(toFormError(e).message);
      },
    });
  }
}
