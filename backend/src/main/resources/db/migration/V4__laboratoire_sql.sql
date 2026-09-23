-- =====================================================================
-- CDA Academy — V4 : base pédagogique du laboratoire SQL
--
-- Les requêtes des apprenantes s'exécutent :
--   * avec un rôle dédié « cda_lab » qui n'a accès qu'au schéma « lab » ;
--   * dans une transaction toujours annulée (ROLLBACK) : aucune modification ne persiste ;
--   * avec un délai maximal d'exécution et un nombre de lignes limité (voir LabExecutor).
-- Les tables du schéma appartiennent au propriétaire de la base : cda_lab ne peut ni les
-- supprimer ni les modifier structurellement.
-- =====================================================================

CREATE SCHEMA lab;

-- ---------------------------------------------------------------------
-- Boutique en ligne
-- ---------------------------------------------------------------------
CREATE TABLE lab.categories (
    id   INTEGER PRIMARY KEY,
    nom  VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE lab.produits (
    id            INTEGER PRIMARY KEY,
    nom           VARCHAR(100)  NOT NULL,
    categorie_id  INTEGER       REFERENCES lab.categories (id),
    prix          NUMERIC(8, 2) NOT NULL CHECK (prix >= 0),
    stock         INTEGER       NOT NULL DEFAULT 0 CHECK (stock >= 0),
    date_ajout    DATE          NOT NULL
);

CREATE TABLE lab.clients (
    id                INTEGER PRIMARY KEY,
    prenom            VARCHAR(50)  NOT NULL,
    nom               VARCHAR(50)  NOT NULL,
    email             VARCHAR(120) NOT NULL UNIQUE,
    ville             VARCHAR(60),
    date_inscription  DATE         NOT NULL
);

CREATE TABLE lab.commandes (
    id             INTEGER PRIMARY KEY,
    client_id      INTEGER     NOT NULL REFERENCES lab.clients (id),
    date_commande  DATE        NOT NULL,
    statut         VARCHAR(15) NOT NULL CHECK (statut IN ('EN_ATTENTE', 'EXPEDIEE', 'LIVREE', 'ANNULEE'))
);

CREATE TABLE lab.lignes_commande (
    commande_id    INTEGER       NOT NULL REFERENCES lab.commandes (id),
    produit_id     INTEGER       NOT NULL REFERENCES lab.produits (id),
    quantite       INTEGER       NOT NULL CHECK (quantite > 0),
    prix_unitaire  NUMERIC(8, 2) NOT NULL CHECK (prix_unitaire >= 0),
    PRIMARY KEY (commande_id, produit_id)
);

CREATE TABLE lab.avis (
    id          INTEGER PRIMARY KEY,
    produit_id  INTEGER  NOT NULL REFERENCES lab.produits (id),
    client_id   INTEGER  NOT NULL REFERENCES lab.clients (id),
    note        SMALLINT NOT NULL CHECK (note BETWEEN 1 AND 5),
    commentaire VARCHAR(300),
    date_avis   DATE     NOT NULL
);

-- ---------------------------------------------------------------------
-- Ressources humaines (auto-jointure, agrégats par service)
-- ---------------------------------------------------------------------
CREATE TABLE lab.services (
    id   INTEGER PRIMARY KEY,
    nom  VARCHAR(50) NOT NULL UNIQUE,
    ville VARCHAR(60) NOT NULL
);

CREATE TABLE lab.employes (
    id             INTEGER PRIMARY KEY,
    prenom         VARCHAR(50)   NOT NULL,
    nom            VARCHAR(50)   NOT NULL,
    poste          VARCHAR(60)   NOT NULL,
    salaire        NUMERIC(8, 2) NOT NULL CHECK (salaire > 0),
    date_embauche  DATE          NOT NULL,
    service_id     INTEGER       REFERENCES lab.services (id),
    manager_id     INTEGER       REFERENCES lab.employes (id)
);

-- ---------------------------------------------------------------------
-- Données
-- ---------------------------------------------------------------------
INSERT INTO lab.categories (id, nom) VALUES
    (1, 'Livres'), (2, 'Informatique'), (3, 'Maison'), (4, 'Sport'),
    (5, 'Jeux'), (6, 'Musique'), (7, 'Papeterie'), (8, 'Jardin');

INSERT INTO lab.produits (id, nom, categorie_id, prix, stock, date_ajout) VALUES
    (1,  'Apprendre SQL pas à pas',          1,  29.90, 42, '2024-01-15'),
    (2,  'Java pour les débutants',          1,  34.50, 18, '2024-02-03'),
    (3,  'Angular en pratique',              1,  39.00,  0, '2024-03-21'),
    (4,  'Roman : Les jardins de Ramata',    1,  18.90, 65, '2024-05-10'),
    (5,  'Clavier mécanique',                2,  89.99, 12, '2024-01-20'),
    (6,  'Souris sans fil',                  2,  24.99, 57, '2024-01-20'),
    (7,  'Écran 27 pouces',                  2, 249.00,  7, '2024-04-02'),
    (8,  'Casque audio',                     2,  79.90, 23, '2024-06-11'),
    (9,  'Disque SSD 1 To',                  2, 109.00,  0, '2024-07-01'),
    (10, 'Lampe de bureau',                  3,  32.00, 30, '2024-02-14'),
    (11, 'Plaid en laine',                   3,  45.00, 15, '2024-10-05'),
    (12, 'Théière en fonte',                 3,  54.90,  9, '2024-11-18'),
    (13, 'Tapis de yoga',                    4,  27.50, 40, '2024-03-01'),
    (14, 'Haltères 2 × 5 kg',                4,  49.90, 11, '2024-03-01'),
    (15, 'Gourde isotherme',                 4,  19.90, 80, '2024-05-22'),
    (16, 'Jeu de société : Pixel Quest',     5,  36.00, 25, '2024-09-09'),
    (17, 'Puzzle 1000 pièces',               5,  16.50, 33, '2024-09-09'),
    (18, 'Jeu de cartes Algo Duel',          5,  12.90, 70, '2024-12-01'),
    (19, 'Vinyle : Jazz du dimanche',        6,  27.00,  6, '2024-04-19'),
    (20, 'Ukulélé soprano',                  6,  59.00,  4, '2024-08-30'),
    (21, 'Carnet pointillé A5',              7,   9.90, 120, '2024-01-05'),
    (22, 'Stylos gel (lot de 10)',           7,   8.50, 95, '2024-01-05'),
    (23, 'Surligneurs pastel',               7,   6.90, 60, '2024-02-27'),
    (24, 'Kit de graines aromatiques',       8,  14.90, 45, '2025-03-10'),
    (25, 'Arrosoir en zinc',                 8,  29.00, 13, '2025-03-10'),
    (26, 'Sécateur ergonomique',             8,  22.50,  0, '2025-04-02'),
    (27, 'Webcam HD',                        2,  49.00, 19, '2025-01-12'),
    (28, 'Coffret surprise',              NULL,  25.00, 10, '2025-02-01'),
    (29, 'Docker et conteneurs',             1,  42.00,  8, '2025-05-15'),
    (30, 'Spring Boot : le guide',           1,  45.00, 14, '2025-06-01');

INSERT INTO lab.clients (id, prenom, nom, email, ville, date_inscription) VALUES
    (1,  'Awa',      'Diallo',    'awa.diallo@exemple.fr',       'Paris',     '2023-11-02'),
    (2,  'Lucas',    'Martin',    'lucas.martin@exemple.fr',     'Lyon',      '2023-12-15'),
    (3,  'Inès',     'Benali',    'ines.benali@exemple.fr',      'Marseille', '2024-01-08'),
    (4,  'Hugo',     'Bernard',   'hugo.bernard@exemple.fr',     'Paris',     '2024-01-22'),
    (5,  'Fatou',    'Sow',       'fatou.sow@exemple.fr',        'Lille',     '2024-02-10'),
    (6,  'Léa',      'Petit',     'lea.petit@exemple.fr',        'Nantes',    '2024-02-18'),
    (7,  'Mamadou',  'Keita',     'mamadou.keita@exemple.fr',    'Paris',     '2024-03-05'),
    (8,  'Chloé',    'Robert',    'chloe.robert@exemple.fr',     NULL,        '2024-03-19'),
    (9,  'Yanis',    'Haddad',    'yanis.haddad@exemple.fr',     'Toulouse',  '2024-04-01'),
    (10, 'Camille',  'Richard',   'camille.richard@exemple.fr',  'Bordeaux',  '2024-04-14'),
    (11, 'Aminata',  'Traoré',    'aminata.traore@exemple.fr',   'Lyon',      '2024-05-03'),
    (12, 'Nathan',   'Durand',    'nathan.durand@exemple.fr',    'Lille',     '2024-05-27'),
    (13, 'Sarah',    'Lefèvre',   'sarah.lefevre@exemple.fr',    'Paris',     '2024-06-09'),
    (14, 'Omar',     'Cissé',     'omar.cisse@exemple.fr',       'Marseille', '2024-07-12'),
    (15, 'Manon',    'Moreau',    'manon.moreau@exemple.fr',     NULL,        '2024-08-01'),
    (16, 'Théo',     'Simon',     'theo.simon@exemple.fr',       'Nantes',    '2024-09-15'),
    (17, 'Kadiatou', 'Camara',    'kadiatou.camara@exemple.fr',  'Paris',     '2024-10-02'),
    (18, 'Louis',    'Laurent',   'louis.laurent@exemple.fr',    'Strasbourg','2024-11-20'),
    (19, 'Jade',     'Michel',    'jade.michel@exemple.fr',      'Lyon',      '2025-01-06'),
    (20, 'Ibrahim',  'Koné',      'ibrahim.kone@exemple.fr',     'Toulouse',  '2025-02-14'),
    (21, 'Emma',     'Garcia',    'emma.garcia@exemple.fr',      'Bordeaux',  '2025-03-03'),
    (22, 'Rayan',    'Fofana',    'rayan.fofana@exemple.fr',     'Paris',     '2025-04-11'),
    (23, 'Zoé',      'David',     'zoe.david@exemple.fr',        'Montpellier','2025-05-19'),
    (24, 'Moussa',   'Diakité',   'moussa.diakite@exemple.fr',   'Lille',     '2025-06-02'),
    (25, 'Alice',    'Bertrand',  'alice.bertrand@exemple.fr',   NULL,        '2025-06-28');

INSERT INTO lab.commandes (id, client_id, date_commande, statut) VALUES
    (1,  1,  '2024-01-10', 'LIVREE'),
    (2,  2,  '2024-01-18', 'LIVREE'),
    (3,  1,  '2024-02-02', 'LIVREE'),
    (4,  3,  '2024-02-20', 'LIVREE'),
    (5,  4,  '2024-03-01', 'ANNULEE'),
    (6,  5,  '2024-03-12', 'LIVREE'),
    (7,  7,  '2024-03-28', 'LIVREE'),
    (8,  2,  '2024-04-05', 'LIVREE'),
    (9,  6,  '2024-04-22', 'LIVREE'),
    (10, 9,  '2024-05-06', 'LIVREE'),
    (11, 1,  '2024-05-19', 'LIVREE'),
    (12, 10, '2024-06-02', 'LIVREE'),
    (13, 11, '2024-06-15', 'ANNULEE'),
    (14, 7,  '2024-07-01', 'LIVREE'),
    (15, 13, '2024-07-14', 'LIVREE'),
    (16, 3,  '2024-08-03', 'LIVREE'),
    (17, 14, '2024-08-25', 'LIVREE'),
    (18, 5,  '2024-09-09', 'LIVREE'),
    (19, 16, '2024-09-30', 'LIVREE'),
    (20, 2,  '2024-10-12', 'LIVREE'),
    (21, 17, '2024-10-28', 'LIVREE'),
    (22, 13, '2024-11-15', 'LIVREE'),
    (23, 1,  '2024-11-29', 'LIVREE'),
    (24, 18, '2024-12-08', 'LIVREE'),
    (25, 7,  '2024-12-20', 'LIVREE'),
    (26, 19, '2025-01-15', 'LIVREE'),
    (27, 20, '2025-02-20', 'ANNULEE'),
    (28, 11, '2025-03-08', 'LIVREE'),
    (29, 21, '2025-03-22', 'LIVREE'),
    (30, 4,  '2025-04-05', 'LIVREE'),
    (31, 22, '2025-04-18', 'LIVREE'),
    (32, 9,  '2025-05-02', 'EXPEDIEE'),
    (33, 1,  '2025-05-20', 'EXPEDIEE'),
    (34, 23, '2025-06-01', 'EXPEDIEE'),
    (35, 17, '2025-06-10', 'EXPEDIEE'),
    (36, 2,  '2025-06-15', 'EN_ATTENTE'),
    (37, 24, '2025-06-20', 'EN_ATTENTE'),
    (38, 13, '2025-06-25', 'EN_ATTENTE'),
    (39, 7,  '2025-06-28', 'EN_ATTENTE'),
    (40, 10, '2025-06-30', 'EN_ATTENTE');

INSERT INTO lab.lignes_commande (commande_id, produit_id, quantite, prix_unitaire) VALUES
    (1, 1, 1, 29.90), (1, 21, 2, 9.90),
    (2, 5, 1, 89.99), (2, 6, 1, 24.99),
    (3, 2, 1, 34.50),
    (4, 13, 1, 27.50), (4, 15, 2, 19.90),
    (5, 7, 1, 249.00),
    (6, 4, 1, 18.90), (6, 22, 1, 8.50), (6, 23, 1, 6.90),
    (7, 8, 1, 79.90),
    (8, 3, 1, 39.00), (8, 1, 1, 29.90),
    (9, 10, 1, 32.00), (9, 11, 1, 45.00),
    (10, 16, 1, 36.00), (10, 17, 1, 16.50),
    (11, 7, 1, 249.00), (11, 6, 1, 24.99),
    (12, 19, 2, 27.00),
    (13, 14, 1, 49.90),
    (14, 9, 1, 109.00), (14, 5, 1, 89.99),
    (15, 12, 1, 54.90), (15, 10, 1, 32.00),
    (16, 18, 3, 12.90),
    (17, 20, 1, 59.00),
    (18, 21, 5, 9.90), (18, 22, 2, 8.50),
    (19, 13, 2, 27.50), (19, 14, 1, 49.90),
    (20, 2, 1, 34.50), (20, 3, 1, 39.00),
    (21, 4, 2, 18.90),
    (22, 16, 1, 36.00), (22, 18, 2, 12.90),
    (23, 8, 1, 79.90), (23, 15, 1, 19.90),
    (24, 11, 2, 45.00),
    (25, 1, 2, 29.90), (25, 2, 1, 34.50),
    (26, 27, 1, 49.00), (26, 6, 1, 24.99),
    (27, 7, 1, 249.00),
    (28, 24, 3, 14.90), (28, 25, 1, 29.00),
    (29, 29, 1, 42.00), (29, 30, 1, 45.00),
    (30, 26, 1, 22.50), (30, 24, 1, 14.90),
    (31, 5, 1, 89.99), (31, 27, 1, 49.00), (31, 8, 1, 79.90),
    (32, 30, 1, 45.00),
    (33, 29, 1, 42.00), (33, 21, 3, 9.90),
    (34, 17, 2, 16.50),
    (35, 12, 1, 54.90),
    (36, 9, 1, 109.00),
    (37, 15, 4, 19.90), (37, 13, 1, 27.50),
    (38, 20, 1, 59.00),
    (39, 30, 2, 45.00),
    (40, 23, 2, 6.90), (40, 22, 1, 8.50);

INSERT INTO lab.avis (id, produit_id, client_id, note, commentaire, date_avis) VALUES
    (1,  1,  1,  5, 'Clair et progressif, parfait pour débuter.',      '2024-01-25'),
    (2,  5,  2,  4, 'Agréable à utiliser, un peu bruyant.',            '2024-01-30'),
    (3,  6,  2,  3, NULL,                                              '2024-02-01'),
    (4,  2,  1,  4, 'Bonnes explications sur les classes.',            '2024-02-15'),
    (5,  13, 3,  5, 'Épais et antidérapant.',                          '2024-03-02'),
    (6,  4,  5,  5, 'Un roman lumineux.',                              '2024-03-25'),
    (7,  8,  7,  4, 'Très bon son pour le prix.',                      '2024-04-10'),
    (8,  3,  2,  5, 'Indispensable pour les formulaires réactifs.',    '2024-04-20'),
    (9,  11, 6,  5, 'Tout doux.',                                      '2024-05-02'),
    (10, 16, 9,  4, 'Parties rapides et stratégiques.',                '2024-05-20'),
    (11, 7,  1,  5, 'Couleurs superbes.',                              '2024-06-01'),
    (12, 19, 10, 3, 'Belle pochette, pressage moyen.',                 '2024-06-18'),
    (13, 9,  7,  5, 'Démarrage du PC en quelques secondes.',           '2024-07-12'),
    (14, 12, 13, 4, NULL,                                              '2024-07-29'),
    (15, 18, 3,  2, 'Règles confuses.',                                '2024-08-15'),
    (16, 20, 14, 5, 'Idéal pour apprendre.',                           '2024-09-02'),
    (17, 21, 5,  4, 'Papier épais.',                                   '2024-09-20'),
    (18, 14, 16, 3, 'Correct.',                                        '2024-10-10'),
    (19, 4,  17, 4, 'Belle écriture.',                                 '2024-11-05'),
    (20, 16, 13, 5, 'On y joue tous les week-ends.',                   '2024-11-28'),
    (21, 11, 18, 4, NULL,                                              '2024-12-18'),
    (22, 27, 19, 3, 'Image correcte, micro faible.',                   '2025-01-28'),
    (23, 24, 11, 5, 'Tout a poussé !',                                 '2025-03-30'),
    (24, 29, 21, 5, 'Enfin compris les volumes.',                      '2025-04-02'),
    (25, 5,  22, 5, 'Frappe très précise.',                            '2025-04-28'),
    (26, 8,  22, 2, 'Arceau fragile.',                                 '2025-05-03');

INSERT INTO lab.services (id, nom, ville) VALUES
    (1, 'Direction', 'Paris'),
    (2, 'Développement', 'Lyon'),
    (3, 'Support client', 'Lille'),
    (4, 'Logistique', 'Marseille');

INSERT INTO lab.employes (id, prenom, nom, poste, salaire, date_embauche, service_id, manager_id) VALUES
    (1,  'Nadia',   'Mercier',  'Directrice générale',     6800.00, '2015-09-01', 1, NULL),
    (2,  'Karim',   'Benaïssa', 'Responsable technique',   5200.00, '2017-03-15', 2, 1),
    (3,  'Julie',   'Fontaine', 'Développeuse full stack', 3600.00, '2020-06-01', 2, 2),
    (4,  'Samuel',  'Ndiaye',   'Développeur back-end',    3400.00, '2021-09-13', 2, 2),
    (5,  'Clara',   'Rousseau', 'Développeuse front-end',  3300.00, '2022-01-10', 2, 2),
    (6,  'Adama',   'Coulibaly','Alternant développeur',   1450.00, '2024-09-02', 2, 3),
    (7,  'Élise',   'Garnier',  'Responsable support',     3900.00, '2018-05-22', 3, 1),
    (8,  'Bilal',   'Amrani',   'Conseiller support',      2300.00, '2021-02-01', 3, 7),
    (9,  'Pauline', 'Lemoine',  'Conseillère support',     2250.00, '2023-04-17', 3, 7),
    (10, 'Olivier', 'Chevalier','Responsable logistique',  3700.00, '2016-11-07', 4, 1),
    (11, 'Mariam',  'Touré',    'Préparatrice de commandes', 2100.00, '2022-07-04', 4, 10),
    (12, 'Victor',  'Blanc',    'Consultant externe',      4100.00, '2025-01-06', NULL, 1);

-- ---------------------------------------------------------------------
-- Copie de référence (inaccessible au laboratoire) : permet de restaurer les données
-- à chaque démarrage de l'application (voir LabMaintenance).
-- ---------------------------------------------------------------------
CREATE SCHEMA lab_seed;
CREATE TABLE lab_seed.categories     AS TABLE lab.categories;
CREATE TABLE lab_seed.produits       AS TABLE lab.produits;
CREATE TABLE lab_seed.clients        AS TABLE lab.clients;
CREATE TABLE lab_seed.commandes      AS TABLE lab.commandes;
CREATE TABLE lab_seed.lignes_commande AS TABLE lab.lignes_commande;
CREATE TABLE lab_seed.avis           AS TABLE lab.avis;
CREATE TABLE lab_seed.services       AS TABLE lab.services;
CREATE TABLE lab_seed.employes       AS TABLE lab.employes;

-- ---------------------------------------------------------------------
-- Rôle restreint du laboratoire
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'cda_lab') THEN
        EXECUTE format('CREATE ROLE cda_lab LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT CONNECTION LIMIT 20 PASSWORD %L',
                       '${labPassword}');
    END IF;
END
$$;

-- Garde-fous appliqués à chaque session du rôle
ALTER ROLE cda_lab SET statement_timeout = '3s';
ALTER ROLE cda_lab SET lock_timeout = '1s';
ALTER ROLE cda_lab SET idle_in_transaction_session_timeout = '10s';
ALTER ROLE cda_lab SET work_mem = '4MB';
ALTER ROLE cda_lab SET search_path = lab;

-- Aucun accès aux tables de l'application (schéma public) ni aux tables temporaires
REVOKE ALL ON SCHEMA public FROM PUBLIC;
DO $$
BEGIN
    EXECUTE format('REVOKE TEMPORARY ON DATABASE %I FROM PUBLIC', current_database());
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO cda_lab', current_database());
END
$$;

-- Lecture et écriture des données du schéma lab (toujours annulées), création d'objets temporaires à l'exercice
GRANT USAGE, CREATE ON SCHEMA lab TO cda_lab;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA lab TO cda_lab;
