import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { CourseSummary, JuryCard } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { IconComponent } from '../../shared/icon.component';
import { MarkdownComponent } from '../../shared/markdown.component';

/** Entraînement à l'oral : une question, réponse à voix haute, réponse modèle, auto-évaluation. */
@Component({
  selector: 'app-jury-trainer',
  imports: [FormsModule, RouterLink, IconComponent, MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './jury-trainer.component.html',
  styleUrl: './jury-trainer.component.scss',
})
export class JuryTrainerComponent implements OnInit {
  private readonly api = inject(ApiService);

  /** ?revoir=1 : seulement les questions marquées « à revoir ». */
  readonly revoir = input<string | undefined>(undefined);

  readonly courses = signal<CourseSummary[]>([]);
  readonly course = signal('');
  readonly all = signal(false);
  readonly onlyReview = signal(false);

  readonly cards = signal<JuryCard[] | null>(null);
  readonly index = signal(0);
  readonly shown = signal(false);
  readonly results = signal<{ known: number; review: number }>({ known: 0, review: 0 });
  readonly error = signal<string | null>(null);

  readonly current = computed(() => this.cards()?.[this.index()] ?? null);
  readonly finished = computed(() => {
    const c = this.cards();
    return !!c && c.length > 0 && this.index() >= c.length;
  });

  ngOnInit(): void {
    this.onlyReview.set(this.revoir() === '1');
    this.api.courses().subscribe({ next: (c) => this.courses.set(c) });
    this.startSession();
  }

  startSession(): void {
    this.cards.set(null);
    this.error.set(null);
    this.index.set(0);
    this.shown.set(false);
    this.results.set({ known: 0, review: 0 });
    this.api
      .jurySession({ course: this.course() || undefined, all: this.all(), toReview: this.onlyReview(), size: 15 })
      .subscribe({
        next: (c) => this.cards.set(c),
        error: (e: unknown) => this.error.set(toFormError(e).message),
      });
  }

  answer(known: boolean): void {
    const card = this.current();
    if (!card) {
      return;
    }
    this.api.reviewJury(card.key, known).subscribe({ error: () => undefined });
    this.results.update((r) => (known ? { ...r, known: r.known + 1 } : { ...r, review: r.review + 1 }));
    this.shown.set(false);
    this.index.update((i) => i + 1);
    setTimeout(() => document.getElementById('jury-question')?.focus());
  }
}
