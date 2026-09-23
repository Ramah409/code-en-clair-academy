import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { CatalogItem, DIFFICULTY_NAMES, EXERCISE_KIND_LABELS, LabRun } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { RewardService } from '../../core/ui/reward.service';
import { CodeViewComponent } from '../../shared/code-view.component';
import { IconComponent } from '../../shared/icon.component';
import { McdEditorComponent } from '../../shared/modeling/mcd-editor.component';
import { MldViewComponent } from '../../shared/modeling/mld-editor.component';
import { McdModel, MldModel, emptyMcd, mcdToMld, mldToSql } from '../../shared/modeling/model';

const DRAFT_KEY = 'cda-atelier-mcd';

/** Exemple chargé à la première visite : une petite médiathèque. */
const EXAMPLE: McdModel = {
  entities: [
    { id: 'e1', name: 'Adherent', attributes: [{ name: 'id_adherent', identifier: true }, { name: 'nom' }, { name: 'email' }], x: 40, y: 40 },
    { id: 'e2', name: 'Livre', attributes: [{ name: 'id_livre', identifier: true }, { name: 'titre' }, { name: 'annee' }], x: 560, y: 40 },
    { id: 'e3', name: 'Auteur', attributes: [{ name: 'id_auteur', identifier: true }, { name: 'nom' }], x: 560, y: 300 },
  ],
  associations: [
    { id: 'a1', name: 'emprunter', attributes: [{ name: 'date_emprunt' }], links: [{ entity: 'e1', card: '0,n' }, { entity: 'e2', card: '0,n' }], x: 300, y: 60 },
    { id: 'a2', name: 'ecrire', attributes: [], links: [{ entity: 'e3', card: '1,n' }, { entity: 'e2', card: '1,1' }], x: 600, y: 190 },
  ],
};

@Component({
  selector: 'app-modeling',
  imports: [FormsModule, RouterLink, CodeViewComponent, IconComponent, McdEditorComponent, MldViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './modeling.component.html',
  styleUrl: './modeling.component.scss',
})
export class ModelingComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly rewards = inject(RewardService);

  readonly tab = signal<'exercices' | 'atelier'>('exercices');
  readonly exercises = signal<CatalogItem[]>([]);
  readonly kindLabels = EXERCISE_KIND_LABELS;
  readonly difficulties = DIFFICULTY_NAMES;

  // Atelier
  readonly start = signal<McdModel>(EXAMPLE);
  readonly model = signal<McdModel>(EXAMPLE);
  readonly mld = signal<MldModel | null>(null);
  readonly sql = signal<string>('');
  readonly run = signal<LabRun | null>(null);
  readonly runError = signal<string | null>(null);
  readonly title = signal('Mon MCD');
  readonly diagramId = signal<number | null>(null);
  readonly saved = signal<{ id: number; title: string; updatedAt: string }[]>([]);
  readonly saving = signal(false);

  readonly groups = computed(() =>
    (['MCD', 'MCD_VERS_MLD', 'MLD_VERS_SQL'] as const).map((kind) => ({
      kind,
      label: kind === 'MCD' ? 'Construire un MCD' : kind === 'MCD_VERS_MLD' ? 'Passer du MCD au MLD' : 'Écrire le MPD en SQL',
      items: this.exercises().filter((e) => e.kind === kind),
    })),
  );

  ngOnInit(): void {
    this.api.exercises().subscribe((list) =>
      this.exercises.set(list.filter((e) => ['MCD', 'MCD_VERS_MLD', 'MLD_VERS_SQL'].includes(e.kind))),
    );
    this.api.diagrams().subscribe((d) => this.saved.set(d));
    try {
      const draft = localStorage.getItem(DRAFT_KEY);
      if (draft) {
        const parsed = JSON.parse(draft) as McdModel;
        this.start.set(parsed);
        this.model.set(parsed);
      }
    } catch {
      // Brouillon illisible ou stockage indisponible : on garde l'exemple.
    }
  }

  onModel(m: McdModel): void {
    this.model.set(m);
    this.mld.set(null);
    this.sql.set('');
    this.run.set(null);
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify(m));
    } catch {
      // Stockage indisponible : le brouillon n'est pas conservé.
    }
  }

  generate(): void {
    const mld = mcdToMld(this.model());
    this.mld.set(mld);
    this.sql.set(mldToSql(mld));
    this.run.set(null);
  }

  test(): void {
    this.runError.set(null);
    this.api.runSql(this.sql()).subscribe({
      next: (r) => this.run.set(r),
      error: (e: unknown) => this.runError.set(toFormError(e).message),
    });
  }

  reset(example: boolean): void {
    const m = example ? EXAMPLE : emptyMcd();
    this.start.set(structuredClone(m));
    this.diagramId.set(null);
    this.title.set(example ? 'Médiathèque' : 'Mon MCD');
    this.onModel(m);
  }

  save(): void {
    this.saving.set(true);
    this.api.saveDiagram(this.diagramId(), this.title(), this.model()).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.diagramId.set(d.id);
        this.rewards.info('Modèle enregistré', d.title);
        this.api.diagrams().subscribe((list) => this.saved.set(list));
      },
      error: (e: unknown) => {
        this.saving.set(false);
        this.rewards.error('Enregistrement impossible', toFormError(e).message);
      },
    });
  }

  open(id: number): void {
    this.api.diagram(id).subscribe((d) => {
      this.diagramId.set(d.id);
      this.title.set(d.title);
      this.start.set(d.content);
      this.onModel(d.content);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  remove(id: number): void {
    this.api.deleteDiagram(id).subscribe(() => {
      this.saved.update((list) => list.filter((d) => d.id !== id));
      if (this.diagramId() === id) {
        this.diagramId.set(null);
      }
    });
  }
}
