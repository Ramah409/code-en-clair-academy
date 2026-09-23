import { ChangeDetectionStrategy, Component, ElementRef, computed, input, output, signal, viewChild } from '@angular/core';

import { McdModel } from './model';

interface Box {
  id: string;
  kind: 'entity' | 'association';
  x: number;
  y: number;
  w: number;
  h: number;
  name: string;
  lines: { text: string; identifier: boolean }[];
}

const CHAR = 7.4;

/**
 * Diagramme MCD en notation Merise : entités (rectangles, identifiants soulignés), associations
 * (ellipses) et cardinalités sur chaque patte. Les formes sont déplaçables à la souris ou au doigt.
 */
@Component({
  selector: 'app-mcd-diagram',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg #svg class="mcd" [attr.viewBox]="viewBox()" role="img" [attr.aria-label]="description()"
         (pointermove)="onMove($event)" (pointerup)="endDrag()" (pointerleave)="endDrag()">
      <defs>
        <pattern id="mcd-grid" width="24" height="24" patternUnits="userSpaceOnUse">
          <circle cx="1" cy="1" r="1" class="mcd__dot" />
        </pattern>
      </defs>
      <rect x="-2000" y="-2000" width="6000" height="6000" fill="url(#mcd-grid)" />

      @for (l of links(); track $index) {
        <line [attr.x1]="l.x1" [attr.y1]="l.y1" [attr.x2]="l.x2" [attr.y2]="l.y2" class="mcd__link" />
        <text [attr.x]="l.lx" [attr.y]="l.ly" class="mcd__card" text-anchor="middle">{{ l.card }}</text>
      }

      @for (b of boxes(); track b.id) {
        <g [attr.transform]="'translate(' + b.x + ',' + b.y + ')'" class="mcd__shape"
           [class.mcd__shape--selected]="selected() === b.id" [class.mcd__shape--draggable]="editable()"
           (pointerdown)="startDrag($event, b)">
          @if (b.kind === 'entity') {
            <rect [attr.width]="b.w" [attr.height]="b.h" rx="6" class="mcd__entity" />
            <rect [attr.width]="b.w" height="28" rx="6" class="mcd__entity-head" />
            <rect y="22" [attr.width]="b.w" height="6" class="mcd__entity-head" />
            <text [attr.x]="b.w / 2" y="19" text-anchor="middle" class="mcd__title">{{ b.name }}</text>
            @for (line of b.lines; track $index) {
              <text x="10" [attr.y]="46 + $index * 19" class="mcd__attr" [class.mcd__attr--id]="line.identifier">{{ line.text }}</text>
            }
          } @else {
            <ellipse [attr.cx]="b.w / 2" [attr.cy]="b.h / 2" [attr.rx]="b.w / 2" [attr.ry]="b.h / 2" class="mcd__assoc" />
            <text [attr.x]="b.w / 2" [attr.y]="b.lines.length ? 24 : b.h / 2 + 5" text-anchor="middle" class="mcd__title mcd__title--assoc">{{ b.name }}</text>
            @for (line of b.lines; track $index) {
              <text [attr.x]="b.w / 2" [attr.y]="44 + $index * 17" text-anchor="middle" class="mcd__attr">{{ line.text }}</text>
            }
          }
        </g>
      }
      @if (!boxes().length) {
        <text x="200" y="120" text-anchor="middle" class="mcd__empty">Ajoute une entité pour commencer ton MCD.</text>
      }
    </svg>
  `,
  styles: [
    `
      :host { display: block; }
      .mcd { width: 100%; height: 100%; min-height: 320px; display: block; touch-action: none; user-select: none; }
      .mcd__dot { fill: var(--border-strong); }
      .mcd__link { stroke: var(--text-muted); stroke-width: 1.6; }
      .mcd__card { font: 700 12px var(--font-mono); fill: var(--rose-700); paint-order: stroke; stroke: var(--bg); stroke-width: 4px; }
      .mcd__entity { fill: var(--bg-elevated); stroke: var(--primary); stroke-width: 1.6; }
      .mcd__entity-head { fill: var(--primary); }
      .mcd__title { font: 700 13px var(--font-body); fill: #fff; }
      .mcd__title--assoc { fill: var(--text); }
      .mcd__assoc { fill: var(--accent-soft); stroke: var(--accent); stroke-width: 1.6; }
      .mcd__attr { font: 500 12px var(--font-mono); fill: var(--text); }
      .mcd__attr--id { text-decoration: underline; font-weight: 700; }
      .mcd__shape--draggable { cursor: grab; }
      .mcd__shape--selected .mcd__entity, .mcd__shape--selected .mcd__assoc { stroke-width: 3; }
      .mcd__empty { font: 500 14px var(--font-body); fill: var(--text-muted); }
    `,
  ],
})
export class McdDiagramComponent {
  readonly model = input.required<McdModel>();
  readonly editable = input(false);
  readonly selected = input<string | null>(null);

  readonly moved = output<{ id: string; x: number; y: number }>();
  readonly picked = output<string>();

  private readonly svg = viewChild.required<ElementRef<SVGSVGElement>>('svg');
  private readonly drag = signal<{ id: string; dx: number; dy: number } | null>(null);

  readonly boxes = computed<Box[]>(() => {
    const m = this.model();
    const entities: Box[] = m.entities.map((e) => {
      const lines = e.attributes.map((a) => ({ text: a.name, identifier: !!a.identifier }));
      const w = Math.max(120, 22 + CHAR * Math.max(e.name.length + 2, ...lines.map((l) => l.text.length)));
      return { id: e.id, kind: 'entity', x: e.x, y: e.y, w, h: 36 + Math.max(1, lines.length) * 19, name: e.name, lines };
    });
    const assocs: Box[] = m.associations.map((a) => {
      const lines = a.attributes.map((at) => ({ text: at.name, identifier: false }));
      const w = Math.max(110, 40 + CHAR * Math.max(a.name.length, ...lines.map((l) => l.text.length)));
      return { id: a.id, kind: 'association', x: a.x, y: a.y, w, h: lines.length ? 40 + lines.length * 17 : 46, name: a.name, lines };
    });
    return [...entities, ...assocs];
  });

  readonly links = computed(() => {
    const byId = new Map(this.boxes().map((b) => [b.id, b]));
    const result: { x1: number; y1: number; x2: number; y2: number; lx: number; ly: number; card: string }[] = [];
    for (const a of this.model().associations) {
      const ab = byId.get(a.id);
      if (!ab) {
        continue;
      }
      a.links.forEach((l, i) => {
        const eb = byId.get(l.entity);
        if (!eb) {
          return;
        }
        const reflexiveOffset = a.links.filter((x) => x.entity === l.entity).length > 1 ? (i % 2 ? 22 : -22) : 0;
        const x1 = ab.x + ab.w / 2;
        const y1 = ab.y + ab.h / 2;
        const x2 = eb.x + eb.w / 2 + reflexiveOffset;
        const y2 = eb.y + eb.h / 2;
        // Étiquette placée à la sortie de la patte, juste avant le bord de l'entité
        const t = edgeRatio(x1, y1, x2, y2, eb);
        result.push({ x1, y1, x2, y2, lx: x1 + (x2 - x1) * t, ly: y1 + (y2 - y1) * t - 6, card: l.card });
      });
    }
    return result;
  });

  readonly viewBox = computed(() => {
    const boxes = this.boxes();
    if (!boxes.length) {
      return '0 0 400 240';
    }
    const minX = Math.min(...boxes.map((b) => b.x)) - 40;
    const minY = Math.min(...boxes.map((b) => b.y)) - 40;
    const maxX = Math.max(...boxes.map((b) => b.x + b.w)) + 40;
    const maxY = Math.max(...boxes.map((b) => b.y + b.h)) + 40;
    return `${minX} ${minY} ${Math.max(400, maxX - minX)} ${Math.max(240, maxY - minY)}`;
  });

  /** Description textuelle complète du modèle, pour les lecteurs d'écran. */
  readonly description = computed(() => {
    const m = this.model();
    const names = new Map(m.entities.map((e) => [e.id, e.name]));
    const ents = m.entities.map((e) => {
      const ids = e.attributes.filter((a) => a.identifier).map((a) => a.name);
      const others = e.attributes.filter((a) => !a.identifier).map((a) => a.name);
      return `${e.name} (identifiant ${ids.join(', ') || 'aucun'}${others.length ? ' ; ' + others.join(', ') : ''})`;
    });
    const assocs = m.associations.map(
      (a) => `${a.name} entre ${a.links.map((l) => `${names.get(l.entity) ?? '?'} (${l.card})`).join(' et ')}`,
    );
    return `Diagramme MCD. Entités : ${ents.join(' ; ') || 'aucune'}. Associations : ${assocs.join(' ; ') || 'aucune'}.`;
  });

  startDrag(event: PointerEvent, box: Box): void {
    this.picked.emit(box.id);
    if (!this.editable()) {
      return;
    }
    const p = this.toSvg(event);
    this.drag.set({ id: box.id, dx: p.x - box.x, dy: p.y - box.y });
    (event.target as Element).setPointerCapture?.(event.pointerId);
  }

  onMove(event: PointerEvent): void {
    const d = this.drag();
    if (!d) {
      return;
    }
    const p = this.toSvg(event);
    this.moved.emit({ id: d.id, x: Math.round((p.x - d.dx) / 8) * 8, y: Math.round((p.y - d.dy) / 8) * 8 });
  }

  endDrag(): void {
    this.drag.set(null);
  }

  private toSvg(event: PointerEvent): { x: number; y: number } {
    const svg = this.svg().nativeElement;
    const pt = svg.createSVGPoint();
    pt.x = event.clientX;
    pt.y = event.clientY;
    const ctm = svg.getScreenCTM();
    return ctm ? pt.matrixTransform(ctm.inverse()) : { x: event.clientX, y: event.clientY };
  }
}

/** Proportion du segment à laquelle la ligne atteint le bord du rectangle cible (moins une marge). */
function edgeRatio(x1: number, y1: number, x2: number, y2: number, b: Box): number {
  const dx = x2 - x1;
  const dy = y2 - y1;
  const tx = dx === 0 ? Infinity : Math.abs(b.w / 2 / dx);
  const ty = dy === 0 ? Infinity : Math.abs(b.h / 2 / dy);
  const inside = Math.min(tx, ty, 1);
  return Math.max(0.15, 1 - inside - 0.12);
}
