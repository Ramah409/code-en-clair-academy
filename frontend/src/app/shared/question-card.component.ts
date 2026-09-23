import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Answer, DIFFICULTY_LABELS, Feedback, PublicQuestion } from '../core/api/models';
import { CodeViewComponent } from './code-view.component';
import { IconComponent } from './icon.component';
import { MarkdownComponent } from './markdown.component';

const KIND_LABELS: Record<string, string> = {
  CHOIX_UNIQUE: 'QCM',
  CHOIX_MULTIPLE: 'Plusieurs réponses',
  VRAI_FAUX: 'Vrai ou faux',
  COMPLETER_CODE: 'Code à compléter',
  RESULTAT_CODE: 'Que renvoie ce code ?',
  TEXTE: 'Réponse courte',
};

/**
 * Question interactive (QCM, vrai/faux, code à compléter, résultat de code).
 * - mode « immediate » : bouton Valider, puis correction détaillée affichée par le parent ;
 * - mode « deferred » (examen) : chaque sélection est transmise, sans correction.
 */
@Component({
  selector: 'app-question-card',
  imports: [FormsModule, CodeViewComponent, IconComponent, MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="qcard" [class.qcard--ok]="feedback()?.correct" [class.qcard--ko]="feedback() && !feedback()!.correct"
             [attr.aria-labelledby]="'q-' + uid">
      <header class="qcard__head">
        <span class="qcard__kind">{{ kindLabel() }}</span>
        @if (showMeta()) {
          <span class="qcard__meta">{{ difficultyLabel() }}@if (question().theme) { — {{ question().theme }} }</span>
        }
      </header>

      <div class="qcard__prompt" [id]="'q-' + uid"><app-markdown [md]="question().prompt" /></div>

      @if (question().snippet) {
        <app-code-view [code]="question().snippet!" [language]="question().language || 'text'" />
      }

      @if (isText()) {
        <label class="qcard__text">
          <span class="qcard__text-label">{{ question().kind === 'COMPLETER_CODE' ? 'Ce qui remplace ____' : 'Ta réponse' }}</span>
          <input class="input qcard__input" type="text" autocomplete="off" spellcheck="false"
                 [ngModel]="text()" (ngModelChange)="onText($event)" [disabled]="locked()"
                 (keydown.enter)="validate()" (change)="saveDeferred()" />
        </label>
        @if (feedback(); as fb) {
          <p class="qcard__accepted">
            <app-icon [name]="fb.correct ? 'check' : 'x'" [size]="18" />
            @if (fb.correct) {
              Bonne réponse.
            } @else {
              Réponse attendue : <code>{{ fb.acceptedAnswers[0] }}</code>
            }
          </p>
        }
      } @else {
        @if (multiple()) {
          <p class="qcard__hint">Plusieurs réponses possibles : coche toutes les bonnes.</p>
        }
        <ul class="qcard__choices" [attr.role]="multiple() ? 'group' : 'radiogroup'" [attr.aria-labelledby]="'q-' + uid">
          @for (c of displayedChoices(); track c.position) {
            <li>
              <button type="button" class="choice"
                      [attr.role]="multiple() ? 'checkbox' : 'radio'"
                      [attr.aria-checked]="isSelected(c.position)"
                      [class.choice--selected]="isSelected(c.position)"
                      [class.choice--correct]="c.status === 'correct'"
                      [class.choice--wrong]="c.status === 'wrong'"
                      [class.choice--missed]="c.status === 'missed'"
                      [disabled]="locked()"
                      (click)="toggle(c.position)">
                <span class="choice__mark" aria-hidden="true">
                  @switch (c.status) {
                    @case ('correct') { <app-icon name="check" [size]="16" /> }
                    @case ('wrong') { <app-icon name="x" [size]="16" /> }
                    @case ('missed') { <app-icon name="check" [size]="16" /> }
                  }
                </span>
                <span class="choice__label"><app-markdown [md]="c.label" [inline]="true" /></span>
              </button>
              @if (c.why) {
                <p class="choice__why" [class.choice__why--good]="c.correct">
                  <app-markdown [md]="c.why" [inline]="true" />
                </p>
              }
            </li>
          }
        </ul>
      }

      @if (feedback(); as fb) {
        <div class="qcard__verdict" role="status">
          <p class="qcard__verdict-title">
            <app-icon [name]="fb.correct ? 'check' : 'lightbulb'" [size]="20" />
            {{ fb.correct ? 'Bonne réponse' : 'Pas encore : regardons pourquoi' }}
          </p>
          <app-markdown [md]="fb.explanation" />
          @if (!fb.correct && reviewable()) {
            <p class="qcard__review-hint">Relis le passage concerné, puis réessaie : ta réponse ne te fait perdre aucun point.</p>
          }
        </div>
      }

      @if (mode() === 'immediate') {
        <div class="qcard__actions">
          @if (!feedback()) {
            <button type="button" class="btn" [disabled]="!hasAnswer() || busy()" (click)="validate()">
              {{ busy() ? 'Vérification…' : 'Valider' }}
            </button>
          } @else if (!feedback()!.correct && allowRetry()) {
            @if (reviewable()) {
              <button type="button" class="btn btn--ghost" (click)="review.emit()">
                <app-icon name="book" [size]="18" /> Revoir le passage
              </button>
            }
            <button type="button" class="btn btn--secondary" (click)="retry.emit()">
              <app-icon name="refresh" [size]="18" /> Réessayer
            </button>
          }
        </div>
      }
    </section>
  `,
})
export class QuestionCardComponent {
  private static counter = 0;
  readonly uid = ++QuestionCardComponent.counter;

  readonly question = input.required<PublicQuestion>();
  readonly feedback = input<Feedback | null | undefined>(null);
  readonly given = input<Answer | null | undefined>(null);
  readonly mode = input<'immediate' | 'deferred'>('immediate');
  readonly busy = input(false);
  readonly disabled = input(false);
  readonly allowRetry = input(true);
  readonly showMeta = input(false);
  /** Affiche « Revoir le passage » après une erreur (la leçon sait où renvoyer). */
  readonly reviewable = input(false);

  readonly answer = output<Answer>();
  readonly retry = output<void>();
  readonly review = output<void>();

  readonly selected = signal<number[]>([]);
  readonly text = signal('');

  readonly kindLabel = computed(() => KIND_LABELS[this.question().kind] ?? 'Question');
  readonly difficultyLabel = computed(() => DIFFICULTY_LABELS[this.question().difficulty] ?? '');
  readonly isText = computed(() => ['COMPLETER_CODE', 'TEXTE'].includes(this.question().kind));
  readonly multiple = computed(() => this.question().kind === 'CHOIX_MULTIPLE');
  readonly locked = computed(() => this.disabled() || (this.mode() === 'immediate' && !!this.feedback()));
  readonly hasAnswer = computed(() => (this.isText() ? this.text().trim().length > 0 : this.selected().length > 0));

  /** Choix affichés, enrichis de la correction lorsqu'elle est disponible. */
  readonly displayedChoices = computed(() => {
    const fb = this.feedback();
    const details = new Map((fb?.choices ?? []).map((c) => [c.position, c]));
    return this.question().choices.map((c) => {
      const d = details.get(c.position);
      let status: 'correct' | 'wrong' | 'missed' | null = null;
      if (d) {
        status = d.correct && d.selected ? 'correct' : !d.correct && d.selected ? 'wrong' : d.correct ? 'missed' : null;
      }
      return { ...c, status, why: d?.why, correct: d?.correct ?? false };
    });
  });

  constructor() {
    // Restaure une réponse déjà donnée (reprise d'un QCM, question déjà validée)
    effect(() => {
      const g = this.given();
      this.question();
      this.selected.set(g?.choices ?? []);
      this.text.set(g?.text ?? '');
    });
  }

  isSelected(position: number): boolean {
    return this.selected().includes(position);
  }

  toggle(position: number): void {
    if (this.locked()) {
      return;
    }
    if (this.multiple()) {
      this.selected.update((s) => (s.includes(position) ? s.filter((p) => p !== position) : [...s, position]));
    } else {
      this.selected.set([position]);
    }
    if (this.mode() === 'deferred') {
      this.answer.emit({ choices: this.selected() });
    }
  }

  onText(value: string): void {
    this.text.set(value);
  }

  saveDeferred(): void {
    if (this.mode() === 'deferred') {
      this.validate();
    }
  }

  validate(): void {
    if (this.locked() || !this.hasAnswer()) {
      return;
    }
    this.answer.emit(this.isText() ? { text: this.text() } : { choices: this.selected() });
  }
}
