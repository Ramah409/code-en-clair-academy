import { Injectable, inject, signal } from '@angular/core';

import { ApiService } from '../api/api.service';
import { LabTable } from '../api/models';

/** Schéma de la base pédagogique, chargé une fois (explorateur et autocomplétion SQL). */
@Injectable({ providedIn: 'root' })
export class LabSchemaStore {
  private readonly api = inject(ApiService);
  private loading = false;

  readonly tables = signal<LabTable[]>([]);
  readonly completion = signal<Record<string, string[]> | undefined>(undefined);

  load(): void {
    if (this.loading || this.tables().length) {
      return;
    }
    this.loading = true;
    this.api.labSchema().subscribe({
      next: (tables) => {
        this.tables.set(tables);
        this.completion.set(Object.fromEntries(tables.map((t) => [t.name, t.columns.map((c) => c.name)])));
      },
      error: () => (this.loading = false),
    });
  }
}
