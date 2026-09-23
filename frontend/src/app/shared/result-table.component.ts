import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { ResultData } from '../core/api/models';

/** Tableau de résultat d'une requête SQL (valeurs NULL signalées visuellement). */
@Component({
  selector: 'app-result-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (data(); as d) {
      <div class="result" [attr.aria-label]="caption()">
        <div class="result__scroll" tabindex="0">
          <table class="result__table">
            <caption class="sr-only">{{ caption() }}</caption>
            <thead>
              <tr>
                @for (c of d.columns; track $index) {
                  <th scope="col">{{ c }}</th>
                }
              </tr>
            </thead>
            <tbody>
              @for (row of d.rows; track $index) {
                <tr>
                  @for (cell of row; track $index) {
                    @if (cell === null) {
                      <td class="result__null">NULL</td>
                    } @else {
                      <td>{{ cell }}</td>
                    }
                  }
                </tr>
              } @empty {
                <tr>
                  <td class="result__empty" [attr.colspan]="d.columns.length || 1">Aucune ligne</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <p class="result__meta">
          {{ d.rows.length }} ligne{{ d.rows.length > 1 ? 's' : '' }}{{ d.truncated ? ' (affichage limité)' : '' }}
        </p>
      </div>
    }
  `,
})
export class ResultTableComponent {
  readonly data = input<ResultData | null | undefined>(null);
  readonly caption = input('Résultat de la requête');
}
