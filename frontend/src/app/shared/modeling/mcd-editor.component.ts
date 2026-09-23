import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { IconComponent } from '../icon.component';
import { McdDiagramComponent } from './mcd-diagram.component';
import { CARDINALITIES, McdAssociation, McdEntity, McdModel, emptyMcd, uid } from './model';

/** Éditeur de MCD : diagramme déplaçable et inspecteur accessible au clavier. */
@Component({
  selector: 'app-mcd-editor',
  imports: [FormsModule, IconComponent, McdDiagramComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mcde" [class.mcde--readonly]="readonly()">
      <div class="mcde__canvas">
        <app-mcd-diagram [model]="model()" [editable]="!readonly()" [selected]="selectedId()"
                         (moved)="move($event)" (picked)="selectedId.set($event)" />
      </div>

      @if (!readonly()) {
        <div class="mcde__panel">
          <div class="mcde__tools">
            <button type="button" class="btn btn--sm" (click)="addEntity()"><app-icon name="plus" [size]="16" /> Entité</button>
            <button type="button" class="btn btn--sm btn--secondary" (click)="addAssociation()" [disabled]="model().entities.length < 1">
              <app-icon name="plus" [size]="16" /> Association
            </button>
          </div>

          <ul class="mcde__list" aria-label="Éléments du modèle">
            @for (e of model().entities; track e.id) {
              <li><button type="button" class="mcde__item" [class.mcde__item--on]="selectedId() === e.id" (click)="selectedId.set(e.id)">
                <app-icon name="table" [size]="14" /> {{ e.name }}</button></li>
            }
            @for (a of model().associations; track a.id) {
              <li><button type="button" class="mcde__item mcde__item--assoc" [class.mcde__item--on]="selectedId() === a.id" (click)="selectedId.set(a.id)">
                <app-icon name="link" [size]="14" /> {{ a.name }}</button></li>
            }
          </ul>

          @if (selectedEntity(); as e) {
            <fieldset class="mcde__form">
              <legend>Entité</legend>
              <label class="field"><span>Nom</span>
                <input class="input" [ngModel]="e.name" (ngModelChange)="updateEntity(e.id, { name: $event })" />
              </label>
              <p class="mcde__sub">Attributs <small>(coche l'identifiant)</small></p>
              @for (a of e.attributes; track $index; let i = $index) {
                <div class="mcde__row">
                  <input class="input input--sm" [ngModel]="a.name" (ngModelChange)="setEntityAttr(e.id, i, { name: $event })"
                         [attr.aria-label]="'Nom de l’attribut ' + (i + 1)" />
                  <label class="mcde__id" title="Identifiant">
                    <input type="checkbox" [ngModel]="!!a.identifier" (ngModelChange)="setEntityAttr(e.id, i, { identifier: $event })" /> id
                  </label>
                  <button type="button" class="icon-btn" (click)="removeEntityAttr(e.id, i)" [attr.aria-label]="'Supprimer l’attribut ' + a.name">
                    <app-icon name="x" [size]="16" /></button>
                </div>
              }
              <button type="button" class="btn btn--ghost btn--sm" (click)="addEntityAttr(e.id)"><app-icon name="plus" [size]="14" /> Attribut</button>
              <button type="button" class="btn btn--ghost btn--sm mcde__delete" (click)="removeEntity(e.id)"><app-icon name="trash" [size]="14" /> Supprimer l'entité</button>
            </fieldset>
          }

          @if (selectedAssociation(); as a) {
            <fieldset class="mcde__form">
              <legend>Association</legend>
              <label class="field"><span>Nom (un verbe)</span>
                <input class="input" [ngModel]="a.name" (ngModelChange)="updateAssociation(a.id, { name: $event })" />
              </label>
              <p class="mcde__sub">Pattes et cardinalités</p>
              @for (l of a.links; track $index; let i = $index) {
                <div class="mcde__row">
                  <select class="select select--sm" [ngModel]="l.entity" (ngModelChange)="setLink(a.id, i, { entity: $event })"
                          [attr.aria-label]="'Entité de la patte ' + (i + 1)">
                    @for (e of model().entities; track e.id) { <option [value]="e.id">{{ e.name }}</option> }
                  </select>
                  <select class="select select--sm mcde__card" [ngModel]="l.card" (ngModelChange)="setLink(a.id, i, { card: $event })"
                          [attr.aria-label]="'Cardinalité de la patte ' + (i + 1)">
                    @for (c of cards; track c) { <option [value]="c">{{ c }}</option> }
                  </select>
                  <button type="button" class="icon-btn" (click)="removeLink(a.id, i)" [disabled]="a.links.length <= 2"
                          aria-label="Supprimer la patte"><app-icon name="x" [size]="16" /></button>
                </div>
              }
              <button type="button" class="btn btn--ghost btn--sm" (click)="addLink(a.id)"><app-icon name="plus" [size]="14" /> Patte (association ternaire)</button>
              <p class="mcde__sub">Attributs portés par l'association</p>
              @for (at of a.attributes; track $index; let i = $index) {
                <div class="mcde__row">
                  <input class="input input--sm" [ngModel]="at.name" (ngModelChange)="setAssocAttr(a.id, i, $event)"
                         [attr.aria-label]="'Attribut ' + (i + 1)" />
                  <button type="button" class="icon-btn" (click)="removeAssocAttr(a.id, i)" aria-label="Supprimer l'attribut"><app-icon name="x" [size]="16" /></button>
                </div>
              }
              <button type="button" class="btn btn--ghost btn--sm" (click)="addAssocAttr(a.id)"><app-icon name="plus" [size]="14" /> Attribut</button>
              <button type="button" class="btn btn--ghost btn--sm mcde__delete" (click)="removeAssociation(a.id)"><app-icon name="trash" [size]="14" /> Supprimer l'association</button>
            </fieldset>
          }

          @if (!selectedId()) {
            <p class="mcde__help">Sélectionne un élément dans la liste ou sur le diagramme pour le modifier. Les formes se déplacent à la souris.</p>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      .mcde { display: grid; gap: var(--space-3); }
      @media (min-width: 980px) { .mcde:not(.mcde--readonly) { grid-template-columns: minmax(0, 1fr) 300px; } }
      .mcde__canvas { border: 1px solid var(--border-strong); border-radius: var(--radius-md); background: var(--bg); min-height: 340px; overflow: hidden; }
      .mcde__panel { display: grid; gap: var(--space-3); align-content: start; }
      .mcde__tools { display: flex; gap: var(--space-2); flex-wrap: wrap; }
      .mcde__list { display: flex; flex-wrap: wrap; gap: 6px; margin: 0; padding: 0; list-style: none; }
      .mcde__item { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; border: 1px solid var(--border-strong); border-radius: 99px; background: var(--bg-elevated); color: var(--text); font: inherit; font-size: var(--fs-xs); font-weight: 600; cursor: pointer; }
      .mcde__item--assoc { border-style: dashed; }
      .mcde__item--on { border-color: var(--primary); background: var(--primary-soft); }
      .mcde__form { display: grid; gap: var(--space-2); margin: 0; padding: var(--space-3); border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--bg-elevated); }
      .mcde__form legend { padding: 0 6px; font-weight: 700; font-size: var(--fs-sm); }
      .mcde__form .field { margin: 0; }
      .mcde__form .field span { font-size: var(--fs-xs); font-weight: 600; }
      .mcde__sub { margin: var(--space-2) 0 0; font-size: var(--fs-xs); font-weight: 700; color: var(--text-muted); }
      .mcde__row { display: flex; gap: 6px; align-items: center; }
      .mcde__row .input, .mcde__row .select { min-height: 36px; padding: 4px 8px; font-size: var(--fs-sm); }
      .mcde__card { width: 80px; flex: none; }
      .mcde__id { display: inline-flex; gap: 3px; align-items: center; font-size: var(--fs-xs); white-space: nowrap; }
      .mcde__delete { color: var(--danger); justify-self: start; }
      .mcde__help { font-size: var(--fs-sm); color: var(--text-muted); }
    `,
  ],
})
export class McdEditorComponent {
  readonly initial = input<McdModel | null | undefined>(null);
  readonly readonly = input(false);
  readonly modelChange = output<McdModel>();

  readonly model = signal<McdModel>(emptyMcd());
  readonly selectedId = signal<string | null>(null);
  readonly cards = CARDINALITIES;

  readonly selectedEntity = computed(() => this.model().entities.find((e) => e.id === this.selectedId()));
  readonly selectedAssociation = computed(() => this.model().associations.find((a) => a.id === this.selectedId()));

  constructor() {
    effect(() => {
      const init = this.initial();
      untracked(() => this.model.set(init ? structuredClone(init) : emptyMcd()));
    });
  }

  private commit(model: McdModel): void {
    this.model.set(model);
    this.modelChange.emit(model);
  }

  private patchEntity(id: string, fn: (e: McdEntity) => McdEntity): void {
    const m = this.model();
    this.commit({ ...m, entities: m.entities.map((e) => (e.id === id ? fn(e) : e)) });
  }

  private patchAssoc(id: string, fn: (a: McdAssociation) => McdAssociation): void {
    const m = this.model();
    this.commit({ ...m, associations: m.associations.map((a) => (a.id === id ? fn(a) : a)) });
  }

  move(p: { id: string; x: number; y: number }): void {
    const m = this.model();
    this.commit({
      entities: m.entities.map((e) => (e.id === p.id ? { ...e, x: p.x, y: p.y } : e)),
      associations: m.associations.map((a) => (a.id === p.id ? { ...a, x: p.x, y: p.y } : a)),
    });
  }

  addEntity(): void {
    const m = this.model();
    const n = m.entities.length;
    const entity: McdEntity = {
      id: uid('e'),
      name: `Entité ${n + 1}`,
      attributes: [{ name: `id_entite_${n + 1}`, identifier: true }],
      x: 40 + (n % 3) * 320,
      y: 40 + Math.floor(n / 3) * 220,
    };
    this.commit({ ...m, entities: [...m.entities, entity] });
    this.selectedId.set(entity.id);
  }

  addAssociation(): void {
    const m = this.model();
    const [a, b] = [m.entities[0], m.entities[1] ?? m.entities[0]];
    const assoc: McdAssociation = {
      id: uid('a'),
      name: 'associer',
      attributes: [],
      links: [
        { entity: a.id, card: '0,n' },
        { entity: b.id, card: '0,n' },
      ],
      x: (a.x + b.x) / 2 + 60,
      y: Math.max(a.y, b.y) + 140,
    };
    this.commit({ ...m, associations: [...m.associations, assoc] });
    this.selectedId.set(assoc.id);
  }

  updateEntity(id: string, patch: Partial<McdEntity>): void {
    this.patchEntity(id, (e) => ({ ...e, ...patch }));
  }

  updateAssociation(id: string, patch: Partial<McdAssociation>): void {
    this.patchAssoc(id, (a) => ({ ...a, ...patch }));
  }

  addEntityAttr(id: string): void {
    this.patchEntity(id, (e) => ({ ...e, attributes: [...e.attributes, { name: 'attribut', identifier: false }] }));
  }

  setEntityAttr(id: string, i: number, patch: { name?: string; identifier?: boolean }): void {
    this.patchEntity(id, (e) => ({ ...e, attributes: e.attributes.map((a, j) => (j === i ? { ...a, ...patch } : a)) }));
  }

  removeEntityAttr(id: string, i: number): void {
    this.patchEntity(id, (e) => ({ ...e, attributes: e.attributes.filter((_, j) => j !== i) }));
  }

  removeEntity(id: string): void {
    const m = this.model();
    this.commit({
      entities: m.entities.filter((e) => e.id !== id),
      associations: m.associations
        .map((a) => ({ ...a, links: a.links.filter((l) => l.entity !== id) }))
        .filter((a) => a.links.length >= 2),
    });
    this.selectedId.set(null);
  }

  setLink(id: string, i: number, patch: { entity?: string; card?: string }): void {
    this.patchAssoc(id, (a) => ({ ...a, links: a.links.map((l, j) => (j === i ? { ...l, ...patch } : l)) }));
  }

  addLink(id: string): void {
    const first = this.model().entities[0];
    this.patchAssoc(id, (a) => ({ ...a, links: [...a.links, { entity: first.id, card: '0,n' }] }));
  }

  removeLink(id: string, i: number): void {
    this.patchAssoc(id, (a) => ({ ...a, links: a.links.filter((_, j) => j !== i) }));
  }

  addAssocAttr(id: string): void {
    this.patchAssoc(id, (a) => ({ ...a, attributes: [...a.attributes, { name: 'attribut' }] }));
  }

  setAssocAttr(id: string, i: number, name: string): void {
    this.patchAssoc(id, (a) => ({ ...a, attributes: a.attributes.map((x, j) => (j === i ? { name } : x)) }));
  }

  removeAssocAttr(id: string, i: number): void {
    this.patchAssoc(id, (a) => ({ ...a, attributes: a.attributes.filter((_, j) => j !== i) }));
  }

  removeAssociation(id: string): void {
    const m = this.model();
    this.commit({ ...m, associations: m.associations.filter((a) => a.id !== id) });
    this.selectedId.set(null);
  }
}
