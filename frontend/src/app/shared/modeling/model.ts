// Modèles Merise manipulés par l'atelier : MCD (entités / associations) et MLD (tables).

export const CARDINALITIES = ['0,1', '1,1', '0,n', '1,n'] as const;

export interface McdAttribute {
  name: string;
  identifier?: boolean;
}

export interface McdEntity {
  id: string;
  name: string;
  attributes: McdAttribute[];
  x: number;
  y: number;
}

export interface McdLink {
  entity: string;
  card: string;
}

export interface McdAssociation {
  id: string;
  name: string;
  attributes: McdAttribute[];
  links: McdLink[];
  x: number;
  y: number;
}

export interface McdModel {
  entities: McdEntity[];
  associations: McdAssociation[];
}

export interface MldColumn {
  name: string;
  pk: boolean;
  fk: string;
}

export interface MldTable {
  id: string;
  name: string;
  columns: MldColumn[];
}

export interface MldModel {
  tables: MldTable[];
}

export function emptyMcd(): McdModel {
  return { entities: [], associations: [] };
}

export function uid(prefix: string): string {
  return prefix + Math.random().toString(36).slice(2, 9);
}

/** snake_case sans accent : « Date de commande » → date_de_commande. */
export function snake(name: string): string {
  return name
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_|_$/g, '');
}

function maxOne(card: string): boolean {
  return card.endsWith(',1');
}

/**
 * Règles de passage MCD → MLD :
 * 1. chaque entité devient une table, son identifiant devient la clé primaire ;
 * 2. association binaire avec une patte de cardinalité maximale 1 : clé étrangère du côté (x,1),
 *    les attributs de l'association migrent avec elle ;
 * 3. sinon (x,n)–(x,n) ou association ternaire : nouvelle table dont la clé primaire est composée
 *    des clés étrangères vers chaque entité, plus les attributs de l'association.
 */
export function mcdToMld(mcd: McdModel): MldModel {
  const byId = new Map(mcd.entities.map((e) => [e.id, e]));
  const tables = new Map<string, MldTable>();
  const tableName = (e: McdEntity) => snake(e.name) || 'table';
  const pkOf = (e: McdEntity) => {
    const ids = e.attributes.filter((a) => a.identifier);
    return ids.length ? ids : [{ name: 'id_' + snake(e.name), identifier: true }];
  };

  for (const e of mcd.entities) {
    tables.set(e.id, {
      id: uid('t'),
      name: tableName(e),
      columns: [
        ...pkOf(e).map((a) => ({ name: snake(a.name), pk: true, fk: '' })),
        ...e.attributes.filter((a) => !a.identifier).map((a) => ({ name: snake(a.name), pk: false, fk: '' })),
      ],
    });
  }

  const extra: MldTable[] = [];
  for (const a of mcd.associations) {
    const links = a.links.filter((l) => byId.has(l.entity));
    if (links.length < 2) {
      continue;
    }
    const single = links.length === 2 ? links.find((l) => maxOne(l.card)) : undefined;
    if (single) {
      const other = links.find((l) => l !== single)!;
      const holder = tables.get(single.entity)!;
      const target = byId.get(other.entity)!;
      const reflexive = single.entity === other.entity;
      for (const pk of pkOf(target)) {
        const col = reflexive ? `${snake(pk.name)}_${snake(a.name) || 'lien'}` : snake(pk.name);
        holder.columns.push({ name: uniqueName(holder, col), pk: false, fk: tableName(target) });
      }
      for (const attr of a.attributes) {
        holder.columns.push({ name: uniqueName(holder, snake(attr.name)), pk: false, fk: '' });
      }
    } else {
      const table: MldTable = { id: uid('t'), name: snake(a.name) || 'association', columns: [] };
      for (const l of links) {
        const target = byId.get(l.entity)!;
        for (const pk of pkOf(target)) {
          table.columns.push({ name: uniqueName(table, snake(pk.name)), pk: true, fk: tableName(target) });
        }
      }
      for (const attr of a.attributes) {
        table.columns.push({ name: uniqueName(table, snake(attr.name)), pk: false, fk: '' });
      }
      extra.push(table);
    }
  }
  return { tables: [...tables.values(), ...extra] };
}

function uniqueName(table: MldTable, name: string): string {
  let candidate = name;
  let i = 2;
  while (table.columns.some((c) => c.name === candidate)) {
    candidate = `${name}_${i++}`;
  }
  return candidate;
}

/** Notation textuelle du MLD : clé primaire soulignée (rendue en HTML), clé étrangère préfixée par #. */
export function mldText(mld: MldModel): { table: string; columns: { name: string; pk: boolean; fk: string }[] }[] {
  return mld.tables.map((t) => ({ table: t.name, columns: t.columns }));
}

/** Type SQL déduit du nom de colonne (proposition modifiable ensuite). */
export function guessType(column: string): string {
  const c = column.toLowerCase();
  if (/^(date|dt)_|_date$|^date$|naissance|debut|fin$/.test(c)) {
    return 'DATE';
  }
  if (/prix|montant|salaire|total|tarif|solde|cout/.test(c)) {
    return 'NUMERIC(10, 2)';
  }
  if (/^(id|num|numero|code_postal)|_id$|^id_|quantite|^nb_|stock|note|age|annee|duree|places/.test(c)) {
    return 'INTEGER';
  }
  if (/email|mail/.test(c)) {
    return 'VARCHAR(254)';
  }
  if (/^est_|^a_|actif|valide/.test(c)) {
    return 'BOOLEAN';
  }
  if (/description|commentaire|contenu|texte/.test(c)) {
    return 'TEXT';
  }
  return 'VARCHAR(100)';
}

/** MPD : script CREATE TABLE ordonné selon les dépendances (tables référencées d'abord). */
export function mldToSql(mld: MldModel): string {
  const names = new Set(mld.tables.map((t) => t.name));
  const ordered: MldTable[] = [];
  const pending = [...mld.tables];
  while (pending.length) {
    const index = pending.findIndex((t) =>
      t.columns.every((c) => !c.fk || c.fk === t.name || !names.has(c.fk) || ordered.some((o) => o.name === c.fk)),
    );
    ordered.push(...pending.splice(index < 0 ? 0 : index, 1));
  }
  const pkOf = (name: string) => mld.tables.find((t) => t.name === name)?.columns.find((c) => c.pk)?.name ?? 'id';

  return ordered
    .map((t) => {
      const pks = t.columns.filter((c) => c.pk);
      const lines = t.columns.map((c) => {
        const single = pks.length === 1 && c.pk;
        if (single && !c.fk) {
          return `  ${c.name} INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY`;
        }
        let type = c.fk ? 'INTEGER' : guessType(c.name);
        type += c.pk ? ' NOT NULL' : '';
        const ref = c.fk ? ` REFERENCES ${c.fk} (${pkOf(c.fk)})` : '';
        return `  ${c.name} ${type}${ref}${single ? ' PRIMARY KEY' : ''}`;
      });
      if (pks.length > 1) {
        lines.push(`  PRIMARY KEY (${pks.map((c) => c.name).join(', ')})`);
      }
      return `CREATE TABLE ${t.name} (\n${lines.join(',\n')}\n);`;
    })
    .join('\n\n');
}
