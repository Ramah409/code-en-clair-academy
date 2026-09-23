import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Marked } from 'marked';

import { highlight } from './highlight';

const marked = new Marked({
  gfm: true,
  breaks: false,
  renderer: {
    code({ text, lang }) {
      return `<pre class="md-code"><code class="hljs">${highlight(text, lang ?? undefined)}</code></pre>`;
    },
    link({ href, text }) {
      const external = /^https?:/.test(href);
      return `<a href="${href}"${external ? ' target="_blank" rel="noopener"' : ''}>${text}</a>`;
    },
  },
});

/**
 * Rendu Markdown des contenus pédagogiques. Le HTML produit passe par la liaison [innerHTML]
 * d'Angular, qui le nettoie (aucun script ni attribut d'événement ne peut s'exécuter).
 */
@Component({
  selector: 'app-markdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="md" [class.md--inline]="inline()" [innerHTML]="html()"></div>`,
})
export class MarkdownComponent {
  readonly md = input<string | null | undefined>('');
  readonly inline = input(false);

  readonly html = computed(() => {
    const source = this.md() ?? '';
    return this.inline() ? (marked.parseInline(source) as string) : (marked.parse(source) as string);
  });
}
