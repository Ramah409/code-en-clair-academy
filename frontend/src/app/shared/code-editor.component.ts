import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands';
import { css } from '@codemirror/lang-css';
import { html } from '@codemirror/lang-html';
import { java } from '@codemirror/lang-java';
import { javascript } from '@codemirror/lang-javascript';
import { PostgreSQL, sql } from '@codemirror/lang-sql';
import { yaml } from '@codemirror/lang-yaml';
import {
  HighlightStyle,
  bracketMatching,
  indentOnInput,
  syntaxHighlighting,
} from '@codemirror/language';
import { Compartment, EditorState, Extension } from '@codemirror/state';
import {
  EditorView,
  drawSelection,
  highlightActiveLine,
  highlightActiveLineGutter,
  keymap,
  lineNumbers,
  placeholder as placeholderExt,
} from '@codemirror/view';
import { autocompletion, closeBrackets, closeBracketsKeymap, completionKeymap } from '@codemirror/autocomplete';
import { tags as t } from '@lezer/highlight';

/** Couleurs du code : variables CSS, donc compatibles avec le thème clair et le thème sombre. */
const highlightStyle = HighlightStyle.define([
  { tag: [t.keyword, t.operatorKeyword, t.modifier], color: 'var(--code-keyword)', fontWeight: '600' },
  { tag: [t.string, t.special(t.string)], color: 'var(--code-string)' },
  { tag: [t.number, t.bool, t.null], color: 'var(--code-number)' },
  { tag: [t.comment, t.lineComment, t.blockComment], color: 'var(--code-comment)', fontStyle: 'italic' },
  { tag: [t.typeName, t.className, t.standard(t.name)], color: 'var(--code-type)' },
  { tag: [t.function(t.variableName), t.function(t.propertyName)], color: 'var(--code-function)' },
  { tag: [t.tagName, t.attributeName], color: 'var(--code-keyword)' },
  { tag: [t.propertyName], color: 'var(--code-property)' },
]);

const editorTheme = EditorView.theme({
  '&': { backgroundColor: 'var(--code-bg)', color: 'var(--code-text)', fontSize: '0.92rem' },
  '.cm-content': { fontFamily: 'var(--font-mono)', caretColor: 'var(--primary)', padding: '10px 0' },
  '.cm-gutters': { backgroundColor: 'var(--code-bg)', color: 'var(--code-gutter)', border: 'none' },
  '.cm-activeLine': { backgroundColor: 'var(--code-active)' },
  '.cm-activeLineGutter': { backgroundColor: 'var(--code-active)' },
  '&.cm-focused': { outline: 'none' },
  '.cm-selectionBackground, &.cm-focused .cm-selectionBackground, ::selection': {
    backgroundColor: 'var(--code-selection) !important',
  },
  '.cm-tooltip': { backgroundColor: 'var(--bg-elevated)', border: '1px solid var(--border)' },
  '.cm-tooltip-autocomplete ul li[aria-selected]': { backgroundColor: 'var(--primary)', color: 'var(--text-on-primary)' },
  '.cm-placeholder': { color: 'var(--code-comment)' },
});

/** Éditeur de code (CodeMirror 6). Ctrl/⌘ + Entrée déclenche l'exécution. */
@Component({
  selector: 'app-code-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #host class="editor" [style.min-height.px]="minHeight()"></div>`,
  styles: [
    `
      .editor {
        border: 1px solid var(--border-strong);
        border-radius: var(--radius-md);
        overflow: hidden;
        background: var(--code-bg);
      }
      .editor:focus-within {
        border-color: var(--primary);
        box-shadow: var(--focus-ring);
      }
      :host ::ng-deep .cm-editor {
        min-height: inherit;
      }
      :host ::ng-deep .cm-scroller {
        min-height: inherit;
      }
    `,
  ],
})
export class CodeEditorComponent implements AfterViewInit, OnDestroy {
  readonly value = input<string>('');
  readonly language = input<string>('sql');
  readonly readonly = input(false);
  readonly minHeight = input(140);
  readonly placeholder = input('');
  readonly ariaLabel = input('Éditeur de code');
  /** Tables et colonnes proposées à l'autocomplétion SQL. */
  readonly schema = input<Record<string, string[]> | undefined>(undefined);

  readonly valueChange = output<string>();
  readonly run = output<void>();

  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('host');
  private view?: EditorView;
  private readonly languageConf = new Compartment();
  private current = '';

  constructor() {
    // Mise à jour externe du contenu (réinitialisation, affichage de la correction…)
    effect(() => {
      const v = this.value();
      if (this.view && v !== this.current) {
        this.current = v;
        this.view.dispatch({ changes: { from: 0, to: this.view.state.doc.length, insert: v } });
      }
    });
    effect(() => {
      const lang = this.languageExtension(this.language(), this.schema());
      this.view?.dispatch({ effects: this.languageConf.reconfigure(lang) });
    });
  }

  ngAfterViewInit(): void {
    this.current = this.value();
    this.view = new EditorView({
      parent: this.host().nativeElement,
      state: EditorState.create({
        doc: this.current,
        extensions: [
          lineNumbers(),
          highlightActiveLineGutter(),
          history(),
          drawSelection(),
          indentOnInput(),
          bracketMatching(),
          closeBrackets(),
          autocompletion(),
          highlightActiveLine(),
          syntaxHighlighting(highlightStyle),
          editorTheme,
          EditorView.lineWrapping,
          placeholderExt(this.placeholder()),
          EditorState.readOnly.of(this.readonly()),
          EditorView.contentAttributes.of({ 'aria-label': this.ariaLabel() }),
          this.languageConf.of(this.languageExtension(this.language(), this.schema())),
          keymap.of([
            { key: 'Mod-Enter', run: () => (this.run.emit(), true) },
            ...closeBracketsKeymap,
            ...defaultKeymap,
            ...historyKeymap,
            ...completionKeymap,
            indentWithTab,
          ]),
          EditorView.updateListener.of((update) => {
            if (update.docChanged) {
              this.current = update.state.doc.toString();
              this.valueChange.emit(this.current);
            }
          }),
        ],
      }),
    });
  }

  focus(): void {
    this.view?.focus();
  }

  ngOnDestroy(): void {
    this.view?.destroy();
  }

  private languageExtension(language: string, schema?: Record<string, string[]>): Extension {
    switch (language) {
      case 'sql':
        return sql({ dialect: PostgreSQL, schema, upperCaseKeywords: true });
      case 'java':
        return java();
      case 'typescript':
      case 'ts':
        return javascript({ typescript: true });
      case 'javascript':
      case 'js':
        return javascript();
      case 'html':
        return html();
      case 'css':
      case 'scss':
        return css();
      case 'yaml':
        return yaml();
      default:
        return [];
    }
  }
}
