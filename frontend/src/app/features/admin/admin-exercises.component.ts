import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { EXERCISE_KIND_LABELS } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { RewardService } from '../../core/ui/reward.service';
import { IconComponent } from '../../shared/icon.component';
import { AdminApiService, AdminCourse, AdminExercise, AdminExerciseRow, CheckResult } from './admin-api.service';

/** Modèle de payload proposé pour chaque type d'exercice. */
const PAYLOADS: Record<string, unknown> = {
  SQL: { mode: 'READ', ordered: false, starter: 'SELECT ' },
  CODE_LIBRE: { language: 'java', starter: '', checks: [{ pattern: 'class\\s+\\w+', message: 'Déclare une classe.' }] },
  CORRIGER_ERREUR: { language: 'java', starter: '// code avec une erreur', checks: [{ pattern: 'erreur', absent: true, message: "L'erreur est toujours là." }] },
  COMPLETER: { language: 'java', template: '[[1]] age = 18;', blanks: [['int']] },
  REMETTRE_ORDRE: { language: 'text', lines: ['Première ligne', 'Deuxième ligne', 'Troisième ligne'] },
  MCD: { expected: { entities: [], associations: [] }, solutionModel: { entities: [], associations: [] } },
  MCD_VERS_MLD: { mcd: { entities: [], associations: [] }, expected: { tables: [] }, solutionModel: { tables: [] } },
  MLD_VERS_SQL: { mode: 'WRITE', check: 'SELECT 1' },
};

function blank(lessonId?: number | null): AdminExercise {
  return {
    slug: '',
    kind: 'COMPLETER',
    title: '',
    difficulty: 'FACILE',
    statement: '',
    criteria: '',
    hints: ['', '', ''],
    solution: '',
    explanation: '',
    xp: 10,
    payload: PAYLOADS['COMPLETER'],
    lessonId: lessonId ?? null,
  };
}

@Component({
  selector: 'app-admin-exercises',
  imports: [FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-exercises.component.html',
})
export class AdminExercisesComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly rewards = inject(RewardService);

  readonly lecon = input<string | undefined>(undefined);

  readonly courses = signal<AdminCourse[]>([]);
  readonly course = signal<number | ''>('');
  readonly search = signal('');
  readonly rows = signal<AdminExerciseRow[]>([]);
  readonly editing = signal<AdminExercise | null>(null);
  readonly payloadText = signal('{}');
  readonly check = signal<CheckResult | null>(null);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly kinds = EXERCISE_KIND_LABELS;
  readonly kindKeys = Object.keys(PAYLOADS);

  ngOnInit(): void {
    this.api.courses().subscribe({ next: (c) => this.courses.set(c) });
    if (Number(this.lecon())) {
      this.open(blank(Number(this.lecon())));
    }
    this.load();
  }

  load(): void {
    this.api.exercises(this.course() || undefined, this.search() || undefined).subscribe({
      next: (r) => this.rows.set(r),
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  private open(e: AdminExercise): void {
    this.editing.set(e);
    this.payloadText.set(JSON.stringify(e.payload ?? {}, null, 2));
    this.check.set(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  edit(row: AdminExerciseRow): void {
    this.api.exercise(row.id).subscribe({ next: (e) => this.open(e), error: (e: unknown) => this.error.set(toFormError(e).message) });
  }

  create(): void {
    this.open(blank(Number(this.lecon()) || null));
  }

  patch<K extends keyof AdminExercise>(key: K, value: AdminExercise[K]): void {
    this.editing.update((e) => (e ? { ...e, [key]: value } : e));
    if (key === 'kind' && !this.editing()?.id) {
      this.payloadText.set(JSON.stringify(PAYLOADS[value as string] ?? {}, null, 2));
    }
  }

  patchHint(i: number, value: string): void {
    this.editing.update((e) => (e ? { ...e, hints: e.hints.map((h, j) => (j === i ? value : h)) } : e));
  }

  save(): void {
    const e = this.editing();
    if (!e) {
      return;
    }
    let payload: unknown;
    try {
      payload = JSON.parse(this.payloadText());
    } catch (err) {
      this.error.set('Le payload JSON est invalide : ' + (err as Error).message);
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.saveExercise(e.id, { ...e, payload }).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.editing.set(res.exercise);
        this.payloadText.set(JSON.stringify(res.exercise.payload, null, 2));
        this.check.set(res.check);
        this.rewards.info('Exercice enregistré', res.exercise.slug);
        this.load();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(toFormError(err).message);
      },
    });
  }

  remove(): void {
    const e = this.editing();
    if (!e?.id || !confirm(`Supprimer l'exercice « ${e.slug} » ?`)) {
      return;
    }
    this.api.deleteExercise(e.id).subscribe({
      next: () => {
        this.editing.set(null);
        this.load();
      },
      error: (err: unknown) => this.error.set(toFormError(err).message),
    });
  }
}
