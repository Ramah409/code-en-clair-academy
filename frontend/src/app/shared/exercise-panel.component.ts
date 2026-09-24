import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api/api.service';
import { DIFFICULTY_NAMES, EXERCISE_KIND_LABELS, ExerciseView, LabRun, SubmitResult } from '../core/api/models';
import { toFormError } from '../core/auth/api-error';
import { LabSchemaStore } from '../core/ui/lab-schema.store';
import { RewardService } from '../core/ui/reward.service';
import { CodeEditorComponent } from './code-editor.component';
import { CodeViewComponent } from './code-view.component';
import { IconComponent } from './icon.component';
import { MarkdownComponent } from './markdown.component';
import { ResultTableComponent } from './result-table.component';
import { JsConsoleComponent } from './js-console.component';
import { JsRunResult, runJavaScript } from './js-runner';
import { McdDiagramComponent } from './modeling/mcd-diagram.component';
import { McdEditorComponent } from './modeling/mcd-editor.component';
import { MldEditorComponent, MldViewComponent } from './modeling/mld-editor.component';
import { McdModel, MldModel } from './modeling/model';

interface OrderLine {
  id: number;
  text: string;
}

/** Exercice pratique complet : énoncé, éditeur adapté au type, indices, correction et validation. */
@Component({
  selector: 'app-exercise-panel',
  imports: [
    FormsModule,
    CodeEditorComponent,
    CodeViewComponent,
    IconComponent,
    MarkdownComponent,
    ResultTableComponent,
    JsConsoleComponent,
    McdDiagramComponent,
    McdEditorComponent,
    MldEditorComponent,
    MldViewComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './exercise-panel.component.html',
})
export class ExercisePanelComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly rewards = inject(RewardService);
  readonly schema = inject(LabSchemaStore);

  readonly initial = input.required<ExerciseView>();
  readonly compact = input(false);
  readonly solvedChange = output<SubmitResult>();

  readonly ex = signal<ExerciseView | null>(null);
  readonly code = signal('');
  readonly blanks = signal<string[]>([]);
  readonly order = signal<OrderLine[]>([]);
  readonly result = signal<SubmitResult | null>(null);
  readonly preview = signal<LabRun | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly confirmSolution = signal(false);
  private started = Date.now();
  private currentCode = '';

  readonly kindLabel = computed(() => EXERCISE_KIND_LABELS[this.ex()?.kind ?? ''] ?? 'Exercice');
  readonly difficultyLabel = computed(() => DIFFICULTY_NAMES[this.ex()?.difficulty ?? ''] ?? '');
  readonly language = computed(() => this.ex()?.payload?.language ?? 'sql');
  readonly isCode = computed(() => ['SQL', 'MLD_VERS_SQL', 'CODE_LIBRE', 'CORRIGER_ERREUR'].includes(this.ex()?.kind ?? ''));
  readonly isSql = computed(() => ['SQL', 'MLD_VERS_SQL'].includes(this.ex()?.kind ?? ''));
  readonly mcd = signal<McdModel | null>(null);
  readonly mld = signal<MldModel | null>(null);
  /** Modèles de départ transmis aux éditeurs (chargement, correction recopiée). */
  readonly mcdStart = signal<McdModel | null>(null);
  readonly mldStart = signal<MldModel | null>(null);
  /** Contrôles détaillés renvoyés par la correction d'un MCD ou d'un MLD. */
  readonly modelChecks = computed<{ element: string; ok: boolean; message: string }[]>(() => {
    const kind = this.ex()?.kind;
    return kind === 'MCD' || kind === 'MCD_VERS_MLD' ? (this.result()?.details ?? []) : [];
  });
  readonly hintsLeft = computed(() => 3 - (this.ex()?.hintsUsed ?? 0));

  /** Modèle du code à trous découpé en segments de texte et en champs. */
  readonly templateParts = computed(() => {
    const template: string = this.ex()?.payload?.template ?? '';
    const parts: { text?: string; blank?: number }[] = [];
    const re = /\[\[(\d+)\]\]/g;
    let last = 0;
    let m: RegExpExecArray | null;
    while ((m = re.exec(template))) {
      parts.push({ text: template.slice(last, m.index) });
      parts.push({ blank: Number(m[1]) - 1 });
      last = m.index + m[0].length;
    }
    parts.push({ text: template.slice(last) });
    return parts;
  });

  readonly isJs = computed(() => ['javascript', 'js'].includes(this.language()) && this.isCode());
  readonly jsResult = signal<JsRunResult | null>(null);

  /** Exécute le code JavaScript dans le navigateur (aperçu, sans validation). */
  async runJs(): Promise<void> {
    this.jsResult.set(await runJavaScript(this.currentCode));
  }

  readonly sqlDetails = computed(() => (this.isSql() ? this.result()?.details : null));

  ngOnInit(): void {
    const ex = this.initial();
    this.ex.set(ex);
    this.restore(ex);
    if (ex.kind === 'SQL' || ex.kind === 'MLD_VERS_SQL') {
      this.schema.load();
    }
  }

  private restore(ex: ExerciseView): void {
    const payload = ex.payload ?? {};
    if (this.isCode()) {
      const start = ex.lastAnswer ?? payload.starter ?? '';
      this.code.set(start);
      this.currentCode = start;
    } else if (ex.kind === 'COMPLETER') {
      let saved: string[] = [];
      try {
        saved = ex.lastAnswer ? JSON.parse(ex.lastAnswer) : [];
      } catch {
        saved = [];
      }
      this.blanks.set(Array.from({ length: payload.blankCount ?? 0 }, (_, i) => saved[i] ?? ''));
    } else if (ex.kind === 'REMETTRE_ORDRE') {
      this.order.set([...(payload.lines ?? [])]);
    } else if (ex.kind === 'MCD') {
      this.mcd.set(this.parse(ex.lastAnswer) ?? (payload.starter && payload.starter.entities ? payload.starter : null));
      this.mcdStart.set(this.mcd());
    } else if (ex.kind === 'MCD_VERS_MLD') {
      this.mld.set(this.parse(ex.lastAnswer) ?? (payload.starter && payload.starter.tables ? payload.starter : null));
      this.mldStart.set(this.mld());
    }
  }

  private parse<T>(value?: string): T | null {
    if (!value) {
      return null;
    }
    try {
      return JSON.parse(value) as T;
    } catch {
      return null;
    }
  }

  onCode(value: string): void {
    this.currentCode = value;
  }

  setBlank(index: number, value: string): void {
    this.blanks.update((b) => b.map((v, i) => (i === index ? value : v)));
  }

  move(index: number, delta: number): void {
    const target = index + delta;
    this.order.update((lines) => {
      if (target < 0 || target >= lines.length) {
        return lines;
      }
      const copy = [...lines];
      [copy[index], copy[target]] = [copy[target], copy[index]];
      return copy;
    });
  }

  /** Aperçu sans validation : exécute la requête dans le laboratoire. */
  test(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.runSql(this.currentCode).subscribe({
      next: (r) => {
        this.preview.set(r);
        this.busy.set(false);
      },
      error: (err: unknown) => {
        this.error.set(toFormError(err).message);
        this.busy.set(false);
      },
    });
  }

  submit(): void {
    const ex = this.ex();
    if (!ex || this.busy()) {
      return;
    }
    let answer: unknown;
    if (this.isCode()) {
      answer = this.currentCode;
    } else if (ex.kind === 'COMPLETER') {
      answer = this.blanks();
    } else if (ex.kind === 'MCD') {
      answer = this.mcd() ?? { entities: [], associations: [] };
    } else if (ex.kind === 'MCD_VERS_MLD') {
      answer = this.mld() ?? { tables: [] };
    } else {
      answer = this.order().map((l) => l.id);
    }
    this.busy.set(true);
    this.error.set(null);
    const seconds = Math.round((Date.now() - this.started) / 1000);
    this.api.submitExercise(ex.slug, answer, seconds).subscribe({
      next: (res) => {
        this.started = Date.now();
        this.result.set(res);
        this.preview.set(null);
        this.busy.set(false);
        this.ex.update((e) =>
          e
            ? {
                ...e,
                solved: e.solved || res.success,
                submissions: e.submissions + 1,
                solutionAvailable: true,
                solution: res.solution ?? e.solution,
                explanation: res.explanation ?? e.explanation,
                solutionModel: res.solutionModel ?? e.solutionModel,
              }
            : e,
        );
        if (res.success) {
          this.rewards.celebrate(res.reward);
          this.solvedChange.emit(res);
        }
      },
      error: (err: unknown) => {
        this.error.set(toFormError(err).message);
        this.busy.set(false);
      },
    });
  }

  nextHint(): void {
    const ex = this.ex();
    if (!ex || this.hintsLeft() <= 0) {
      return;
    }
    this.api.hint(ex.slug).subscribe({
      next: (h) =>
        this.ex.update((e) =>
          e ? { ...e, hintsUsed: h.index, hints: [...e.hints, h.text], solutionAvailable: e.solutionAvailable || h.remaining === 0 } : e,
        ),
      error: (err: unknown) => this.error.set(toFormError(err).message),
    });
  }

  showSolution(): void {
    const ex = this.ex();
    if (!ex) {
      return;
    }
    this.confirmSolution.set(false);
    this.api.solution(ex.slug).subscribe({
      next: (view) => this.ex.set({ ...view }),
      error: (err: unknown) => this.error.set(toFormError(err).message),
    });
  }

  /** Recopie la correction dans l'éditeur, pour l'étudier et la valider. */
  useSolution(): void {
    const e = this.ex();
    if (!e) {
      return;
    }
    if (e.kind === 'MCD' && e.solutionModel) {
      this.mcd.set(structuredClone(e.solutionModel));
      this.mcdStart.set(this.mcd());
    } else if (e.kind === 'MCD_VERS_MLD' && e.solutionModel) {
      this.mld.set(structuredClone(e.solutionModel));
      this.mldStart.set(this.mld());
    } else if (e.solution && this.isCode()) {
      this.code.set(e.solution);
      this.currentCode = e.solution;
    }
  }
}
