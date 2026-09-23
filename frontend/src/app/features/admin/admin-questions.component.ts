import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { toFormError } from '../../core/auth/api-error';
import { RewardService } from '../../core/ui/reward.service';
import { IconComponent } from '../../shared/icon.component';
import { AdminApiService, AdminCourse, AdminQuestion, AdminQuestionRow, Page } from './admin-api.service';

const KINDS: Record<string, string> = {
  CHOIX_UNIQUE: 'Choix unique',
  CHOIX_MULTIPLE: 'Choix multiple',
  VRAI_FAUX: 'Vrai / faux',
  RESULTAT_CODE: 'Résultat d’un code',
  COMPLETER_CODE: 'Code à compléter',
  TEXTE: 'Réponse courte',
};

function blank(lessonId?: number | null): AdminQuestion {
  return {
    code: '',
    kind: 'CHOIX_UNIQUE',
    difficulty: 1,
    prompt: '',
    explanation: '',
    answers: [''],
    choices: [
      { label: '', correct: true, why: '' },
      { label: '', correct: false, why: '' },
      { label: '', correct: false, why: '' },
    ],
    lessonId: lessonId ?? null,
    published: true,
  };
}

/** Banque de questions : recherche, création et modification (choix, explications, réponses acceptées). */
@Component({
  selector: 'app-admin-questions',
  imports: [FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-questions.component.html',
})
export class AdminQuestionsComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly rewards = inject(RewardService);

  /** ?lecon=<id> : création d'une question rattachée à cette leçon. */
  readonly lecon = input<string | undefined>(undefined);

  readonly courses = signal<AdminCourse[]>([]);
  readonly course = signal<number | ''>('');
  readonly search = signal('');
  readonly page = signal<Page<AdminQuestionRow> | null>(null);
  readonly editing = signal<AdminQuestion | null>(null);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly kinds = KINDS;
  readonly kindKeys = Object.keys(KINDS);

  readonly isText = computed(() => ['TEXTE', 'COMPLETER_CODE'].includes(this.editing()?.kind ?? ''));

  ngOnInit(): void {
    this.api.courses().subscribe({ next: (c) => this.courses.set(c) });
    const lesson = Number(this.lecon());
    if (lesson) {
      this.editing.set(blank(lesson));
      this.load(0, lesson);
    } else {
      this.load(0);
    }
  }

  load(page: number, lesson?: number): void {
    this.api
      .questions({ course: this.course() || undefined, lesson, search: this.search() || undefined, page })
      .subscribe({ next: (p) => this.page.set(p), error: (e: unknown) => this.error.set(toFormError(e).message) });
  }

  edit(row: AdminQuestionRow): void {
    this.api.question(row.id).subscribe({
      next: (q) => {
        this.editing.set({ ...q, answers: q.answers.length ? q.answers : [''], choices: q.choices.length ? q.choices : blank().choices });
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  create(): void {
    this.editing.set(blank(Number(this.lecon()) || null));
  }

  patch<K extends keyof AdminQuestion>(key: K, value: AdminQuestion[K]): void {
    this.editing.update((q) => (q ? { ...q, [key]: value } : q));
    if (key === 'kind' && value === 'VRAI_FAUX') {
      this.editing.update((q) => (q ? { ...q, choices: [{ label: 'Vrai', correct: true, why: '' }, { label: 'Faux', correct: false, why: '' }] } : q));
    }
  }

  patchChoice(i: number, patch: Partial<{ label: string; correct: boolean; why: string }>): void {
    this.editing.update((q) => {
      if (!q) {
        return q;
      }
      let choices = q.choices.map((c, j) => (j === i ? { ...c, ...patch } : c));
      if (patch.correct && q.kind !== 'CHOIX_MULTIPLE') {
        choices = choices.map((c, j) => ({ ...c, correct: j === i }));
      }
      return { ...q, choices };
    });
  }

  addChoice(): void {
    this.editing.update((q) => (q ? { ...q, choices: [...q.choices, { label: '', correct: false, why: '' }] } : q));
  }

  removeChoice(i: number): void {
    this.editing.update((q) => (q ? { ...q, choices: q.choices.filter((_, j) => j !== i) } : q));
  }

  patchAnswer(i: number, value: string): void {
    this.editing.update((q) => (q ? { ...q, answers: q.answers.map((a, j) => (j === i ? value : a)) } : q));
  }

  addAnswer(): void {
    this.editing.update((q) => (q ? { ...q, answers: [...q.answers, ''] } : q));
  }

  save(): void {
    const q = this.editing();
    if (!q) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.saveQuestion(q.id, q).subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.editing.set(saved.choices.length || saved.answers.length ? saved : { ...saved, choices: blank().choices });
        this.rewards.info('Question enregistrée', saved.code);
        this.load(this.page()?.page ?? 0, Number(this.lecon()) || undefined);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(toFormError(e).message);
      },
    });
  }

  remove(): void {
    const q = this.editing();
    if (!q?.id || !confirm(`Supprimer la question « ${q.code} » ?`)) {
      return;
    }
    this.api.deleteQuestion(q.id).subscribe({
      next: () => {
        this.editing.set(null);
        this.load(this.page()?.page ?? 0);
      },
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }
}
