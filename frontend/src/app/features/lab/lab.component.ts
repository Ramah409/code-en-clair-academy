import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { LabRun } from '../../core/api/models';
import { LabSchemaStore } from '../../core/ui/lab-schema.store';
import { IconComponent } from '../../shared/icon.component';
import { SqlRunnerComponent } from '../../shared/sql-runner.component';

const HISTORY_KEY = 'cda-lab-history';

const EXAMPLES = [
  { title: 'Produits les plus chers', sql: 'SELECT nom, prix\nFROM produits\nORDER BY prix DESC\nLIMIT 5;' },
  {
    title: 'Chiffre d’affaires par catégorie',
    sql: "SELECT c.nom AS categorie, SUM(l.quantite * l.prix_unitaire) AS chiffre_affaires\nFROM lignes_commande l\nJOIN produits p ON p.id = l.produit_id\nJOIN categories c ON c.id = p.categorie_id\nJOIN commandes co ON co.id = l.commande_id\nWHERE co.statut <> 'ANNULEE'\nGROUP BY c.nom\nORDER BY chiffre_affaires DESC;",
  },
  {
    title: 'Clients sans commande',
    sql: 'SELECT cl.prenom, cl.nom\nFROM clients cl\nLEFT JOIN commandes co ON co.client_id = cl.id\nWHERE co.id IS NULL;',
  },
  {
    title: 'Employés et leur manager',
    sql: "SELECT e.prenom || ' ' || e.nom AS employe, m.prenom || ' ' || m.nom AS manager\nFROM employes e\nLEFT JOIN employes m ON m.id = e.manager_id\nORDER BY manager NULLS FIRST;",
  },
  {
    title: 'Créer, remplir et lire une table',
    sql: "CREATE TABLE notes (id SERIAL PRIMARY KEY, matiere VARCHAR(40) NOT NULL, note NUMERIC(4,2) CHECK (note BETWEEN 0 AND 20));\nINSERT INTO notes (matiere, note) VALUES ('SQL', 16.5), ('Java', 14), ('Angular', 17);\nSELECT matiere, note FROM notes ORDER BY note DESC;",
  },
];

@Component({
  selector: 'app-lab',
  imports: [RouterLink, IconComponent, SqlRunnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lab.component.html',
  styleUrl: './lab.component.scss',
})
export class LabComponent implements OnInit {
  readonly schema = inject(LabSchemaStore);

  readonly examples = EXAMPLES;
  readonly code = signal('SELECT prenom, nom, ville\nFROM clients\nWHERE ville = \'Paris\';');
  readonly editorKey = signal(0);
  readonly history = signal<string[]>([]);
  readonly openTable = signal<string | null>('clients');

  ngOnInit(): void {
    this.schema.load();
    try {
      this.history.set(JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]'));
    } catch {
      this.history.set([]);
    }
  }

  load(sql: string): void {
    this.code.set(sql);
    this.editorKey.update((k) => k + 1);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  remember(run: LabRun): void {
    const sql = run.results.map((r) => r.statement).join(';\n');
    if (!sql) {
      return;
    }
    const list = [sql + ';', ...this.history().filter((h) => h !== sql + ';')].slice(0, 10);
    this.history.set(list);
    try {
      localStorage.setItem(HISTORY_KEY, JSON.stringify(list));
    } catch {
      // Stockage indisponible (navigation privée) : l'historique reste en mémoire.
    }
  }

  toggle(name: string): void {
    this.openTable.set(this.openTable() === name ? null : name);
  }
}
