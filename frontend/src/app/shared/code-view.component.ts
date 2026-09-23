import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { IconComponent } from './icon.component';
import { LANGUAGE_LABELS, highlight } from './highlight';
import { MarkdownComponent } from './markdown.component';

interface Line {
  n: number;
  html: string;
  note?: string;
}

/**
 * Code coloré, avec explications ligne par ligne : chaque ligne annotée est suivie de son
 * commentaire, et un survol (ou le focus clavier) relie la ligne à son explication.
 */
@Component({
  selector: 'app-code-view',
  imports: [IconComponent, MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="code">
      <figcaption class="code__bar">
        <span class="code__lang">{{ languageLabel() }}</span>
        @if (caption()) {
          <span class="code__title">{{ caption() }}</span>
        }
        <button type="button" class="code__copy" (click)="copy()" [attr.aria-label]="copied() ? 'Code copié' : 'Copier le code'">
          <app-icon [name]="copied() ? 'check' : 'save'" [size]="16" />
          {{ copied() ? 'Copié' : 'Copier' }}
        </button>
      </figcaption>
      <ol class="code__lines" [class.code__lines--annotated]="annotated()">
        @for (line of lines(); track line.n) {
          <li class="code__line" [class.code__line--noted]="line.note" [class.code__line--active]="active() === line.n"
              (mouseenter)="line.note && active.set(line.n)" (mouseleave)="active.set(null)">
            <span class="code__num" aria-hidden="true">{{ line.n }}</span>
            <code class="hljs code__src" [innerHTML]="line.html || ' '"></code>
            @if (line.note) {
              <span class="code__note">
                <span class="sr-only">Ligne {{ line.n }} : </span>
                <app-markdown [md]="line.note" [inline]="true" />
              </span>
            }
          </li>
        }
      </ol>
      @if (output()) {
        <div class="code__output">
          <span class="code__output-label">Résultat</span>
          <pre>{{ output() }}</pre>
        </div>
      }
    </figure>
  `,
})
export class CodeViewComponent {
  readonly code = input.required<string>();
  readonly language = input<string>('text');
  readonly caption = input<string | undefined>(undefined);
  readonly notes = input<{ n: number; note: string }[] | undefined>(undefined);
  readonly output = input<string | undefined>(undefined);

  readonly active = signal<number | null>(null);
  readonly copied = signal(false);

  readonly languageLabel = computed(() => LANGUAGE_LABELS[this.language()] ?? this.language());
  readonly annotated = computed(() => (this.notes() ?? []).length > 0);

  readonly lines = computed<Line[]>(() => {
    const notes = new Map((this.notes() ?? []).map((n) => [n.n, n.note]));
    return this.code()
      .replace(/\n$/, '')
      .split('\n')
      .map((text, i) => ({ n: i + 1, html: highlight(text, this.language()), note: notes.get(i + 1) }));
  });

  copy(): void {
    void navigator.clipboard?.writeText(this.code()).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }
}
