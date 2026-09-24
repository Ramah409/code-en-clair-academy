/** Résultat d'une exécution de code JavaScript dans le navigateur. */
export interface JsRunResult {
  lines: { kind: 'log' | 'error'; text: string }[];
  timedOut: boolean;
  durationMs: number;
}

/** Code exécuté dans le Worker : capture console.log / console.error et les erreurs, en français. */
const WORKER_SOURCE = `
const out = [];
const show = (v) => {
  if (typeof v === 'string') return v;
  try { return JSON.stringify(v); } catch { return String(v); }
};
console.log = (...a) => out.push({ kind: 'log', text: a.map(show).join(' ') });
console.info = console.log;
console.warn = console.log;
console.error = (...a) => out.push({ kind: 'error', text: a.map(show).join(' ') });
const traduire = (e) => {
  const m = String(e && e.message || e);
  if (e instanceof ReferenceError) return 'Erreur : ' + m + ' — une variable ou une fonction est utilisée sans avoir été déclarée (vérifie l\\'orthographe).';
  if (e instanceof SyntaxError) return 'Erreur de syntaxe : ' + m + ' — il manque sans doute une parenthèse, une accolade ou un guillemet.';
  if (e instanceof TypeError) return 'Erreur de type : ' + m + ' — une valeur n\\'est pas du type attendu (par exemple undefined).';
  return 'Erreur : ' + m;
};
onmessage = (event) => {
  try {
    new Function(event.data)();
  } catch (e) {
    out.push({ kind: 'error', text: traduire(e) });
  }
  postMessage(out.slice(0, 500));
};
`;

/**
 * Exécute le code de l'apprenante dans un Web Worker isolé : pas d'accès à la page, au stockage ni
 * aux cookies, et arrêt forcé après 2 secondes (boucle infinie).
 */
export function runJavaScript(code: string, timeoutMs = 2000): Promise<JsRunResult> {
  const started = performance.now();
  return new Promise((resolve) => {
    const url = URL.createObjectURL(new Blob([WORKER_SOURCE], { type: 'text/javascript' }));
    const worker = new Worker(url);
    const finish = (result: Omit<JsRunResult, 'durationMs'>) => {
      clearTimeout(timer);
      worker.terminate();
      URL.revokeObjectURL(url);
      resolve({ ...result, durationMs: Math.round(performance.now() - started) });
    };
    const timer = setTimeout(
      () =>
        finish({
          lines: [{ kind: 'error', text: 'Arrêt après 2 secondes : ton code tourne sans fin (une boucle qui ne s’arrête jamais ?).' }],
          timedOut: true,
        }),
      timeoutMs,
    );
    worker.onmessage = (e: MessageEvent) => finish({ lines: e.data, timedOut: false });
    worker.onerror = (e: ErrorEvent) => {
      e.preventDefault();
      finish({ lines: [{ kind: 'error', text: 'Erreur : ' + e.message }], timedOut: false });
    };
    worker.postMessage(code);
  });
}
