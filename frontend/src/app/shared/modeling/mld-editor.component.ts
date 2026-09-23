import { ChangeDetectionStrategy, Component, effect, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { IconComponent } from '../icon.component';
import { MldModel, MldTable, uid } from './model';

/** Notation MLD : clé primaire soulignée, clé étrangère préfixée par #. */
@Component({
  selector: 'app-mld-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mldv" role="list" aria-label="Schéma relationnel (MLD)">
      @for (t of model().tables; track t.id) {
        <p class="mldv__line" role="listitem">
          <strong>{{ t.name }}</strong> (
          @for (c of t.columns; track $index; let last = $last) {
            <span class="mldv__col" [class.mldv__col--pk]="c.pk">{{ c.fk ? '#' : '' }}{{ c.name }}</span>{{ last ? '' : ', ' }}
          }
          )
          @if (hasFk(t)) {
            <small class="mldv__refs">— {{ refs(t) }}</small>
          }
        </p>
      } @empty {
        <p class="muted">Aucune table.</p>
      }
    </div>
  `,
  styles: [
    `
      .mldv { font-family: var(--font-mono); font-size: 0.9rem; display: grid; gap: 6px; }
      .mldv__line { margin: 0; line-height: 1.7; }
      .mldv__col--pk { text-decoration: underline; text-underline-offset: 3px; font-weight: 700; }
      .mldv__refs { font-family: var(--font-body); color: var(--text-muted); }
    `,
  ],
})
export class MldViewComponent {
  readonly model = input.required<MldModel>();

  hasFk(t: MldTable): boolean {
    return t.columns.some((c) => c.fk);
  }

  refs(t: MldTable): string {
    return t.columns.filter((c) => c.fk).map((c) => `${c.name} → ${c.fk}`).join(', ');
  }
}

/** Éditeur de MLD : tables, colonnes, clés primaires et étrangères. */
@Component({
  selector: 'app-mld-editor',
  imports: [FormsModule, IconComponent, MldViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mlde">
      <div class="mlde__tables">
        @for (t of model().tables; track t.id; let ti = $index) {
          <fieldset class="mlde__table">
            <legend class="sr-only">Table {{ t.name }}</legend>
            <div class="mlde__head">
              <input class="input mlde__name" [ngModel]="t.name" (ngModelChange)="patchTable(ti, { name: $event })"
                     aria-label="Nom de la table" />
              <button type="button" class="icon-btn" (click)="removeTable(ti)" [attr.aria-label]="'Supprimer la table ' + t.name">
                <app-icon name="trash" [size]="16" /></button>
            </div>
            <div class="mlde__cols">
              <span class="mlde__hdr">Colonne</span><span class="mlde__hdr">Clé primaire</span><span class="mlde__hdr">Clé étrangère vers</span><span></span>
              @for (c of t.columns; track $index; let ci = $index) {
                <input class="input input--sm" [ngModel]="c.name" (ngModelChange)="patchColumn(ti, ci, { name: $event })"
                       [attr.aria-label]="'Nom de la colonne ' + (ci + 1) + ' de ' + t.name" />
                <label class="mlde__pk"><input type="checkbox" [ngModel]="c.pk" (ngModelChange)="patchColumn(ti, ci, { pk: $event })"
                       [attr.aria-label]="c.name + ' fait partie de la clé primaire'" /></label>
                <select class="select select--sm" [ngModel]="c.fk" (ngModelChange)="patchColumn(ti, ci, { fk: $event })"
                        [attr.aria-label]="'Table référencée par ' + c.name">
                  <option value="">—</option>
                  @for (o of model().tables; track o.id) { <option [value]="o.name">{{ o.name }}</option> }
                </select>
                <button type="button" class="icon-btn" (click)="removeColumn(ti, ci)" [attr.aria-label]="'Supprimer la colonne ' + c.name">
                  <app-icon name="x" [size]="16" /></button>
              }
            </div>
            <button type="button" class="btn btn--ghost btn--sm" (click)="addColumn(ti)"><app-icon name="plus" [size]="14" /> Colonne</button>
          </fieldset>
        }
        <button type="button" class="btn btn--sm btn--secondary" (click)="addTable()"><app-icon name="plus" [size]="16" /> Table</button>
      </div>
      <div class="mlde__preview">
        <p class="exo__label">Notation MLD</p>
        <app-mld-view [model]="model()" />
      </div>
    </div>
  `,
  styles: [
    `
      .mlde { display: grid; gap: var(--space-4); }
      @media (min-width: 980px) { .mlde { grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); align-items: start; } }
      .mlde__tables { display: grid; gap: var(--space-3); }
      .mlde__table { margin: 0; padding: var(--space-3); border: 1px solid var(--border-strong); border-radius: var(--radius-md); background: var(--bg-elevated); }
      .mlde__head { display: flex; gap: 6px; margin-bottom: var(--space-2); }
      .mlde__name { font-family: var(--font-mono); font-weight: 700; }
      .mlde__cols { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto; gap: 6px 8px; align-items: center; margin-bottom: var(--space-2); }
      .mlde__cols .input, .mlde__cols .select { min-height: 34px; padding: 3px 8px; font-size: var(--fs-sm); font-family: var(--font-mono); }
      .mlde__hdr { font-size: var(--fs-xs); font-weight: 700; color: var(--text-muted); }
      .mlde__pk { display: grid; place-items: center; }
      .mlde__pk input { width: 18px; height: 18px; accent-color: var(--primary); }
      .mlde__preview { position: sticky; top: var(--space-4); padding: var(--space-3); border-radius: var(--radius-md); background: var(--code-bg); border: 1px solid var(--border); }
    `,
  ],
})
export class MldEditorComponent {
  readonly initial = input<MldModel | null | undefined>(null);
  readonly modelChange = output<MldModel>();
  readonly model = signal<MldModel>({ tables: [] });

  constructor() {
    effect(() => {
      const init = this.initial();
      untracked(() => this.model.set(init ? structuredClone(init) : { tables: [] }));
    });
  }

  private commit(tables: MldTable[]): void {
    const model = { tables };
    this.model.set(model);
    this.modelChange.emit(model);
  }

  addTable(): void {
    const n = this.model().tables.length + 1;
    this.commit([...this.model().tables, { id: uid('t'), name: `table_${n}`, columns: [{ name: 'id', pk: true, fk: '' }] }]);
  }

  removeTable(i: number): void {
    this.commit(this.model().tables.filter((_, j) => j !== i));
  }

  patchTable(i: number, patch: Partial<MldTable>): void {
    const old = this.model().tables[i];
    this.commit(
      this.model().tables.map((t, j) => {
        if (j === i) {
          return { ...t, ...patch };
        }
        // Renommer une table met à jour les clés étrangères qui la référencent
        return patch.name ? { ...t, columns: t.columns.map((c) => (c.fk === old.name ? { ...c, fk: patch.name! } : c)) } : t;
      }),
    );
  }

  addColumn(i: number): void {
    this.commit(this.model().tables.map((t, j) => (j === i ? { ...t, columns: [...t.columns, { name: 'colonne', pk: false, fk: '' }] } : t)));
  }

  patchColumn(i: number, ci: number, patch: Partial<{ name: string; pk: boolean; fk: string }>): void {
    this.commit(
      this.model().tables.map((t, j) =>
        j === i ? { ...t, columns: t.columns.map((c, k) => (k === ci ? { ...c, ...patch } : c)) } : t,
      ),
    );
  }

  removeColumn(i: number, ci: number): void {
    this.commit(this.model().tables.map((t, j) => (j === i ? { ...t, columns: t.columns.filter((_, k) => k !== ci) } : t)));
  }
}
