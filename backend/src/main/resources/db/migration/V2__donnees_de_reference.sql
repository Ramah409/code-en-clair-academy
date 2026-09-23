-- =====================================================================
-- CDA Academy — V2 : données de référence
-- Rôles, paramètres, compétences du référentiel, badges, comptes de démonstration.
-- Les contenus pédagogiques (cours, leçons, exercices...) sont chargés depuis
-- les fichiers versionnés de database/content (voir docs/conception/06-architecture.md).
-- =====================================================================

INSERT INTO roles (code, label) VALUES
    ('USER',  'Apprenante'),
    ('ADMIN', 'Administratrice / formatrice');

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('ollama.model', 'qwen2.5-coder:3b'),
    ('ollama.timeout-seconds', '120'),
    ('tutor.max-requests-per-minute', '10');

-- Compétences (reprennent les notions du référentiel CDA et du cahier des charges)
INSERT INTO skills (code, name, category, description) VALUES
    ('web-fondamentaux',   'Fonctionnement du Web (client/serveur, HTTP, DNS)', 'WEB',      'Comprendre le trajet d''une requête entre le navigateur et le serveur.'),
    ('html-semantique',    'HTML sémantique et formulaires',                    'WEB',      'Structurer une page avec les bonnes balises et des formulaires accessibles.'),
    ('css-mise-en-page',   'CSS, Flexbox, Grid et responsive',                  'WEB',      'Mettre en page une interface adaptée à tous les écrans.'),
    ('accessibilite',      'Accessibilité numérique',                            'WEB',      'Rendre une interface utilisable au clavier et par les technologies d''assistance.'),
    ('javascript-bases',   'JavaScript et DOM',                                  'WEB',      'Manipuler des données et réagir aux événements dans le navigateur.'),
    ('algo-bases',         'Variables, conditions et boucles',                   'ALGO',     'Écrire des algorithmes simples et corrects.'),
    ('algo-fonctions',     'Fonctions, portée et erreurs',                       'ALGO',     'Découper un problème en fonctions réutilisables.'),
    ('poo',                'Programmation orientée objet',                       'ALGO',     'Classes, encapsulation, héritage, polymorphisme et interfaces.'),
    ('algo-tri-recherche', 'Recherche, tri et complexité',                       'ALGO',     'Choisir un algorithme adapté et estimer son coût.'),
    ('merise-mcd',         'Modèle conceptuel de données (MCD)',                 'DONNEES',  'Entités, associations, identifiants et cardinalités.'),
    ('merise-mld-mpd',     'Passage MCD → MLD → MPD',                             'DONNEES',  'Traduire un modèle conceptuel en tables et contraintes.'),
    ('normalisation',      'Normalisation (1FN, 2FN, 3FN)',                       'DONNEES',  'Éliminer les redondances à l''aide des dépendances fonctionnelles.'),
    ('dictionnaire-regles','Dictionnaire de données et règles de gestion',       'DONNEES',  'Recenser les données et formaliser les règles métier.'),
    ('sql-ddl',            'SQL : création de tables et contraintes',            'SQL',      'CREATE TABLE, clés, NOT NULL, UNIQUE, CHECK, DEFAULT.'),
    ('sql-select',         'SQL : interrogation simple',                         'SQL',      'SELECT, WHERE, ORDER BY, LIMIT, LIKE, IN, BETWEEN, NULL.'),
    ('sql-jointures',      'SQL : jointures',                                    'SQL',      'INNER, LEFT, RIGHT JOIN et tables d''association.'),
    ('sql-agregation',     'SQL : agrégation et sous-requêtes',                  'SQL',      'GROUP BY, HAVING, fonctions d''agrégat et sous-requêtes.'),
    ('sql-dml',            'SQL : INSERT, UPDATE, DELETE et transactions',       'SQL',      'Modifier des données de façon sûre et atomique.'),
    ('sql-avance',         'PostgreSQL avancé',                                  'SQL',      'Vues, index, procédures, déclencheurs, rôles et sauvegardes.'),
    ('java-bases',         'Java : syntaxe, types et méthodes',                  'JAVA',     'Écrire un programme Java correct et lisible.'),
    ('java-collections',   'Java : collections, génériques et streams',          'JAVA',     'Manipuler des listes, ensembles et dictionnaires.'),
    ('java-exceptions',    'Java : exceptions et fichiers',                      'JAVA',     'Gérer les erreurs et lire ou écrire des fichiers.'),
    ('java-tests',         'Java : JUnit et Maven',                              'JAVA',     'Tester unitairement et construire un projet.'),
    ('spring-structure',   'Spring Boot : structure et configuration',           'SPRING',   'Organiser un projet en couches Controller / Service / Repository.'),
    ('spring-jpa',         'Spring Data JPA et relations',                       'SPRING',   'Entités, relations, repositories, pagination et tri.'),
    ('spring-rest',        'Contrôleurs REST, DTO et validation',                'SPRING',   'Exposer une API propre avec des codes HTTP adaptés.'),
    ('spring-tests',       'Tests Spring Boot',                                  'SPRING',   'Tests unitaires et d''intégration d''une API.'),
    ('typescript',         'TypeScript',                                         'ANGULAR',  'Typage, interfaces, classes et modules.'),
    ('angular-composants', 'Angular : composants, templates et directives',      'ANGULAR',  'Construire des composants standalone réutilisables.'),
    ('angular-formulaires','Angular : formulaires réactifs',                     'ANGULAR',  'Saisie, validation et messages d''erreur.'),
    ('angular-services',   'Angular : services, HTTP et observables',            'ANGULAR',  'Communiquer avec une API et gérer l''asynchrone.'),
    ('angular-routage',    'Angular : routage, guards et intercepteurs',         'ANGULAR',  'Naviguer et protéger les pages selon le rôle.'),
    ('api-rest',           'Conception d''API REST',                              'API',      'Ressources, méthodes, statuts, en-têtes et JSON.'),
    ('full-stack',         'Communication front / back',                          'API',      'CORS, environnements et propagation des erreurs.'),
    ('auth-jwt',           'Authentification et JWT',                             'SECURITE', 'Émettre, transmettre, vérifier et renouveler un jeton.'),
    ('securite-owasp',     'Injections, XSS, CSRF, CORS',                         'SECURITE', 'Identifier et neutraliser les attaques courantes.'),
    ('rgpd-secrets',       'RGPD, secrets et moindre privilège',                  'SECURITE', 'Protéger les données personnelles et la configuration.'),
    ('git',                'Git et GitHub',                                       'QUALITE',  'Commits, branches, fusions et conflits.'),
    ('tests-strategie',    'Stratégie de tests',                                  'QUALITE',  'Unitaires, intégration, API et bout en bout.'),
    ('docker-deploiement', 'Docker, CI/CD et déploiement',                        'QUALITE',  'Conteneuriser, automatiser et mettre en ligne.'),
    ('gestion-projet',     'Analyse du besoin et gestion de projet',              'PROJET',   'Cahier des charges, MVP, user stories, Agile et Kanban.'),
    ('uml-architecture',   'UML, MVC et architecture trois tiers',                'PROJET',   'Modéliser et justifier une architecture.'),
    ('ux-ui',              'Zoning, wireframes, maquettes et charte',             'PROJET',   'Concevoir une interface avant de la coder.'),
    ('oral-cda',           'Dossier de projet et oral CDA',                       'PROJET',   'Présenter, démontrer et justifier ses choix devant un jury.');

INSERT INTO badges (code, name, description, icon, criteria_type, threshold) VALUES
    ('premiere-lecon',     'Premiers pas',        'Terminer sa première leçon.',                       'footprints', 'LECONS_TERMINEES', 1),
    ('dix-lecons',         'Rythme de croisière', 'Terminer 10 leçons.',                               'route',      'LECONS_TERMINEES', 10),
    ('cinquante-lecons',   'Grande traversée',    'Terminer 50 leçons.',                               'mountain',   'LECONS_TERMINEES', 50),
    ('xp-500',             'Élan',                'Cumuler 500 points d''expérience.',                 'bolt',       'XP_TOTAL', 500),
    ('xp-5000',            'Force tranquille',    'Cumuler 5 000 points d''expérience.',               'star',       'XP_TOTAL', 5000),
    ('serie-7',            'Une semaine pile',    'Travailler 7 jours d''affilée.',                    'flame',      'SERIE_JOURS', 7),
    ('serie-30',           'Discipline',          'Travailler 30 jours d''affilée.',                   'calendar',   'SERIE_JOURS', 30),
    ('exercices-100',      'Cent essais réussis', 'Réussir 100 exercices.',                            'target',     'EXERCICES_REUSSIS', 100),
    ('sql-25',             'Requêteuse',          'Réussir 25 exercices SQL.',                         'database',   'REQUETES_SQL', 25),
    ('mcd-5',              'Architecte des données', 'Enregistrer 5 MCD.',                             'diagram',    'MCD_CREES', 5),
    ('examen-1',           'Examen blanc réussi', 'Réussir un examen blanc CDA.',                      'medal',      'EXAMENS_REUSSIS', 1),
    ('projet-1',           'Bâtisseuse',          'Terminer toutes les étapes d''un projet pratique.', 'hammer',     'PROJETS_TERMINES', 1),
    ('revisions-50',       'Mémoire d''éléphant', 'Effectuer 50 révisions espacées.',                  'brain',      'REVISIONS', 50);

-- Comptes de démonstration (mots de passe documentés dans le README, à changer en production)
--   apprenante@cda-academy.local / Apprenante-2026!
--   formatrice@cda-academy.local / Formatrice-2026!
INSERT INTO users (email, display_name, password_hash, role_id) VALUES
    ('apprenante@cda-academy.local', 'Apprenante démo',
     '$2a$10$QwzDGBmHSIK3BzvVb5gVIuLGCPv1beW2hduliVWXBoBR.UqrUChG.',
     (SELECT id FROM roles WHERE code = 'USER')),
    ('formatrice@cda-academy.local', 'Formatrice démo',
     '$2a$10$Oz7K766.hrrB0ibstQLCrOvs9C9ONWvdhJrcHYSM9mNkScjA7UXRa',
     (SELECT id FROM roles WHERE code = 'ADMIN'));
