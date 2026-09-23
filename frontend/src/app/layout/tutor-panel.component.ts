import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';

import { ApiService } from '../core/api/api.service';
import { TutorMessage } from '../core/api/models';
import { toFormError } from '../core/auth/api-error';
import { IconComponent } from '../shared/icon.component';
import { MarkdownComponent } from '../shared/markdown.component';

/**
 * Assistant pédagogique en panneau latéral, disponible sur toutes les pages.
 * Il connaît la leçon ou l'exercice affiché et répond avec le modèle local (Ollama),
 * ou à partir des cours quand l'IA n'est pas démarrée.
 */
@Component({
  selector: 'app-tutor-panel',
  imports: [FormsModule, RouterLink, IconComponent, MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="tutor-fab" (click)="toggle()" [attr.aria-expanded]="open()"
            aria-controls="tutor-panel" [class.tutor-fab--open]="open()">
      <app-icon [name]="open() ? 'x' : 'sparkle'" [size]="20" />
      <span>{{ open() ? 'Fermer' : 'Assistant' }}</span>
    </button>

    @if (open()) {
      <section id="tutor-panel" class="tutor" role="dialog" aria-modal="false" aria-labelledby="tutor-title">
        <header class="tutor__head">
          <div>
            <h2 id="tutor-title" class="tutor__title">Assistant</h2>
            <p class="tutor__status" [class.tutor__status--off]="!ai()">
              <span class="tutor__dot" aria-hidden="true"></span>
              {{ ai() ? 'IA locale active (' + model() + ')' : 'Mode sans IA : réponses tirées des cours' }}
            </p>
          </div>
          <button type="button" class="icon-btn" (click)="reset()" aria-label="Nouvelle conversation" title="Nouvelle conversation">
            <app-icon name="plus" [size]="18" />
          </button>
        </header>

        @if (contextLabel()) {
          <p class="tutor__context"><app-icon name="book" [size]="14" /> Contexte : {{ contextLabel() }}</p>
        }

        <div class="tutor__messages" #scroller aria-live="polite">
          @if (!messages().length) {
            <div class="tutor__empty">
              <p>Pose ta question avec tes mots, même si elle te paraît simple. Exemples :</p>
              @for (s of suggestions; track s) {
                <button type="button" class="tutor__suggest" (click)="question.set(s)">{{ s }}</button>
              }
            </div>
          }
          @for (m of messages(); track m.id) {
            <article class="tmsg" [class.tmsg--me]="m.author === 'USER'">
              <p class="sr-only">{{ m.author === 'USER' ? 'Toi' : 'Assistant' }} :</p>
              <app-markdown [md]="m.content" />
              @if (m.links?.length) {
                <ul class="tmsg__links">
                  @for (l of m.links; track l.url) {
                    <li><a [routerLink]="l.url" (click)="open.set(false)"><app-icon name="arrow-right" [size]="14" /> {{ l.label }}</a></li>
                  }
                </ul>
              }
            </article>
          }
          @if (busy()) {
            <p class="tutor__typing" role="status">L'assistant réfléchit…</p>
          }
        </div>

        @if (error()) {
          <p class="tutor__error" role="alert">{{ error() }}</p>
        }

        <form class="tutor__form" (ngSubmit)="send()">
          <label for="tutor-q" class="sr-only">Ta question</label>
          <textarea id="tutor-q" name="q" class="input" rows="2" maxlength="2000" placeholder="Ta question…"
                    [ngModel]="question()" (ngModelChange)="question.set($event)"
                    (keydown.enter)="onEnter($event)"></textarea>
          <button type="submit" class="btn" [disabled]="busy() || question().trim().length < 2" aria-label="Envoyer">
            <app-icon name="send" [size]="18" />
          </button>
        </form>
        <p class="tutor__note">Entrée pour envoyer, Maj + Entrée pour aller à la ligne. Les réponses restent sur ton ordinateur.</p>
      </section>
    }
  `,
  styles: [
    `
      :host { position: fixed; right: var(--space-4); bottom: var(--space-4); z-index: 60; display: flex; flex-direction: column; align-items: flex-end; gap: var(--space-2); }
      @media (max-width: 900px) { :host { bottom: calc(72px + env(safe-area-inset-bottom)); right: var(--space-3); } }
      .tutor-fab { display: inline-flex; align-items: center; gap: 8px; order: 2; padding: 10px 16px; border: 0; border-radius: 99px;
        background: var(--accent); color: #fff; font: inherit; font-weight: 700; cursor: pointer; box-shadow: 0 6px 18px rgba(0,0,0,.18); }
      .tutor-fab:focus-visible { outline: 3px solid var(--primary); outline-offset: 3px; }
      .tutor-fab--open { background: var(--primary); }
      .tutor { order: 1; width: min(420px, calc(100vw - 24px)); height: min(620px, calc(100vh - 150px)); display: flex; flex-direction: column;
        border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--bg-elevated); box-shadow: 0 16px 40px rgba(0,0,0,.2); overflow: hidden; }
      .tutor__head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-2); padding: var(--space-3) var(--space-4); border-bottom: 1px solid var(--border); }
      .tutor__title { margin: 0; font-size: var(--fs-lg); }
      .tutor__status { display: flex; align-items: center; gap: 6px; margin: 2px 0 0; font-size: var(--fs-xs); color: var(--success); }
      .tutor__status--off { color: var(--text-muted); }
      .tutor__dot { width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
      .tutor__context { display: flex; align-items: center; gap: 6px; margin: 0; padding: 6px var(--space-4); font-size: var(--fs-xs); background: var(--primary-soft); color: var(--primary-strong, var(--primary)); }
      .tutor__messages { flex: 1; overflow-y: auto; padding: var(--space-3) var(--space-4); display: flex; flex-direction: column; gap: var(--space-3); }
      .tutor__empty p { margin: 0 0 var(--space-2); color: var(--text-muted); font-size: var(--fs-sm); }
      .tutor__suggest { display: block; width: 100%; margin-bottom: 6px; padding: 8px 10px; border: 1px solid var(--border); border-radius: var(--radius-md);
        background: var(--bg); color: inherit; font: inherit; font-size: var(--fs-sm); text-align: left; cursor: pointer; }
      .tutor__suggest:hover { border-color: var(--primary); }
      .tmsg { max-width: 92%; padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); background: var(--bg-sunken); font-size: var(--fs-sm); }
      .tmsg--me { align-self: flex-end; background: var(--primary-soft); }
      .tmsg__links { margin: var(--space-2) 0 0; padding: 0; list-style: none; }
      .tmsg__links a { display: inline-flex; align-items: center; gap: 4px; font-weight: 600; }
      .tutor__typing { margin: 0; font-size: var(--fs-sm); color: var(--text-muted); font-style: italic; }
      .tutor__error { margin: 0; padding: 6px var(--space-4); color: var(--rose-700); font-size: var(--fs-sm); }
      .tutor__form { display: flex; gap: var(--space-2); padding: var(--space-3) var(--space-4) 0; border-top: 1px solid var(--border); }
      .tutor__form textarea { flex: 1; resize: none; min-height: 44px; font-size: var(--fs-sm); }
      .tutor__note { margin: 4px 0 var(--space-3); padding: 0 var(--space-4); font-size: var(--fs-xs); color: var(--text-muted); }
      @media print { :host { display: none; } }
    `,
  ],
})
export class TutorPanelComponent {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  readonly open = signal(false);
  readonly ai = signal(false);
  readonly model = signal('');
  readonly messages = signal<TutorMessage[]>([]);
  readonly question = signal('');
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly url = signal(this.router.url);
  private conversationId: number | null = null;

  readonly suggestions = [
    "C'est quoi une clé étrangère ?",
    'Quelle différence entre 401 et 403 ?',
    'Explique-moi un composant Angular simplement.',
  ];

  /** Leçon ou exercice affiché, transmis à l'assistant. */
  readonly context = computed(() => {
    const path = this.url().split('?')[0];
    const lesson = /^\/lecon\/([^/]+)/.exec(path);
    const exercise = /^\/exercices\/([^/]+)/.exec(path);
    return { lessonSlug: lesson?.[1], exerciseSlug: exercise?.[1] };
  });

  readonly contextLabel = computed(() => {
    const c = this.context();
    return c.lessonSlug ? 'la leçon ouverte' : c.exerciseSlug ? "l'exercice ouvert (indices, pas de solution toute faite)" : '';
  });

  constructor() {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.url.set(this.router.url));
  }

  toggle(): void {
    this.open.update((o) => !o);
    if (this.open()) {
      this.api.tutorStatus().subscribe({
        next: (s) => {
          this.ai.set(s.aiAvailable);
          this.model.set(s.model);
        },
        error: () => this.ai.set(false),
      });
      setTimeout(() => document.getElementById('tutor-q')?.focus());
    }
  }

  reset(): void {
    this.conversationId = null;
    this.messages.set([]);
    this.error.set(null);
    document.getElementById('tutor-q')?.focus();
  }

  onEnter(event: Event): void {
    const e = event as KeyboardEvent;
    if (!e.shiftKey) {
      e.preventDefault();
      this.send();
    }
  }

  send(): void {
    const question = this.question().trim();
    if (question.length < 2 || this.busy()) {
      return;
    }
    const temp: TutorMessage = { id: -Date.now(), author: 'USER', content: question, createdAt: new Date().toISOString() };
    this.messages.update((m) => [...m, temp]);
    this.question.set('');
    this.busy.set(true);
    this.error.set(null);
    this.scrollDown();
    this.api.askTutor({ conversationId: this.conversationId, question, ...this.context() }).subscribe({
      next: (res) => {
        this.conversationId = res.conversationId;
        this.ai.set(res.aiAvailable);
        this.messages.update((m) => [...m, res.answer]);
        this.busy.set(false);
        this.scrollDown();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(toFormError(e).message);
        this.question.set(question);
      },
    });
  }

  private scrollDown(): void {
    setTimeout(() => {
      const el = this.scroller()?.nativeElement;
      el?.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    });
  }
}
