import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { toFormError } from '../../core/auth/api-error';
import { RewardService } from '../../core/ui/reward.service';
import { IconComponent } from '../../shared/icon.component';
import { AdminApiService, AdminLesson } from './admin-api.service';

/** Modèles de blocs insérables dans l'éditeur. */
const SNIPPETS: Record<string, unknown> = {
  texte: { type: 'text', md: 'Une explication courte, une idée à la fois.' },
  définition: { type: 'definition', id: 'def-mot', term: 'Mot technique', md: "Définition simple avec une comparaison de la vie courante." },
  code: { type: 'code', language: 'java', title: 'Exemple', code: 'int age = 18;', lines: [{ n: 1, note: "Explication de la ligne." }] },
  encadré: { type: 'callout', variant: 'tip', title: 'Astuce', md: 'Conseil pratique.' },
  étapes: { type: 'steps', title: 'Étapes', items: ['Première étape', 'Deuxième étape'] },
  comparaison: { type: 'compare', left: { title: 'À éviter', good: false, md: '…' }, right: { title: 'À faire', good: true, md: '…' } },
  question: { type: 'question', ref: 'code-de-la-question', review: 'def-mot' },
  exercice: { type: 'exercise', ref: 'slug-de-l-exercice' },
  'QCM de fin': { type: 'quiz', items: [{ ref: 'code-question-1', review: 'def-mot' }, { ref: 'code-question-2' }] },
  jury: { type: 'jury', items: [{ q: 'Question possible du jury', a: 'Réponse simple.' }] },
};

@Component({
  selector: 'app-admin-lesson',
  imports: [FormsModule, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-lesson.component.html',
})
export class AdminLessonComponent {
  private readonly api = inject(AdminApiService);
  private readonly rewards = inject(RewardService);
  private readonly router = inject(Router);

  readonly id = input.required<string>();
  readonly lesson = signal<AdminLesson | null>(null);
  readonly blocksText = signal('[]');
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly snippetNames = Object.keys(SNIPPETS);

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.load(id));
    });
  }

  private load(id: number): void {
    this.api.lesson(id).subscribe({
      next: (l) => this.apply(l),
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  private apply(l: AdminLesson): void {
    this.lesson.set(l);
    this.blocksText.set(JSON.stringify(l.blocks ?? [], null, 2));
  }

  patch<K extends keyof AdminLesson>(key: K, value: AdminLesson[K]): void {
    this.lesson.update((l) => (l ? { ...l, [key]: value } : l));
  }

  format(): void {
    try {
      this.blocksText.set(JSON.stringify(JSON.parse(this.blocksText()), null, 2));
      this.error.set(null);
    } catch (e) {
      this.error.set('Le JSON des blocs est invalide : ' + (e as Error).message);
    }
  }

  insert(name: string): void {
    try {
      const blocks = JSON.parse(this.blocksText()) as unknown[];
      blocks.push(SNIPPETS[name]);
      this.blocksText.set(JSON.stringify(blocks, null, 2));
    } catch {
      this.error.set('Corrige d’abord le JSON des blocs avant d’insérer un modèle.');
    }
  }

  save(): void {
    const l = this.lesson();
    if (!l) {
      return;
    }
    let blocks: unknown[];
    try {
      blocks = JSON.parse(this.blocksText());
    } catch (e) {
      this.error.set('Le JSON des blocs est invalide : ' + (e as Error).message);
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.updateLesson(l.id, { ...l, blocks }).subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.apply(saved);
        this.rewards.info('Leçon enregistrée', saved.title);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(toFormError(e).message);
      },
    });
  }

  remove(): void {
    const l = this.lesson();
    if (!l || !confirm(`Supprimer la leçon « ${l.title} » ?`)) {
      return;
    }
    this.api.deleteLesson(l.id).subscribe({
      next: () => void this.router.navigate(['/admin/parcours']),
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }
}
