import hljs from 'highlight.js/lib/core';
import bash from 'highlight.js/lib/languages/bash';
import css from 'highlight.js/lib/languages/css';
import dockerfile from 'highlight.js/lib/languages/dockerfile';
import java from 'highlight.js/lib/languages/java';
import javascript from 'highlight.js/lib/languages/javascript';
import json from 'highlight.js/lib/languages/json';
import plaintext from 'highlight.js/lib/languages/plaintext';
import properties from 'highlight.js/lib/languages/properties';
import scss from 'highlight.js/lib/languages/scss';
import sql from 'highlight.js/lib/languages/sql';
import typescript from 'highlight.js/lib/languages/typescript';
import xml from 'highlight.js/lib/languages/xml';
import yaml from 'highlight.js/lib/languages/yaml';

hljs.registerLanguage('bash', bash);
hljs.registerLanguage('css', css);
hljs.registerLanguage('dockerfile', dockerfile);
hljs.registerLanguage('java', java);
hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('json', json);
hljs.registerLanguage('plaintext', plaintext);
hljs.registerLanguage('properties', properties);
hljs.registerLanguage('scss', scss);
hljs.registerLanguage('sql', sql);
hljs.registerLanguage('typescript', typescript);
hljs.registerLanguage('html', xml);
hljs.registerLanguage('xml', xml);
hljs.registerLanguage('yaml', yaml);

const ALIASES: Record<string, string> = { ts: 'typescript', js: 'javascript', sh: 'bash', yml: 'yaml', text: 'plaintext' };

/** Coloration syntaxique : renvoie du HTML échappé enrichi de classes hljs. */
export function highlight(code: string, language?: string): string {
  const lang = ALIASES[language ?? ''] ?? language ?? 'plaintext';
  if (!hljs.getLanguage(lang)) {
    return hljs.highlight(code, { language: 'plaintext' }).value;
  }
  return hljs.highlight(code, { language: lang, ignoreIllegals: true }).value;
}

export const LANGUAGE_LABELS: Record<string, string> = {
  sql: 'SQL',
  java: 'Java',
  typescript: 'TypeScript',
  javascript: 'JavaScript',
  html: 'HTML',
  css: 'CSS',
  scss: 'SCSS',
  json: 'JSON',
  yaml: 'YAML',
  bash: 'Terminal',
  dockerfile: 'Dockerfile',
  properties: 'Properties',
  text: 'Texte',
  plaintext: 'Texte',
};
