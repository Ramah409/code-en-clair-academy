-- =====================================================================
-- CDA Academy — V3 : parcours structurés, banque de QCM, quiz et progression
-- Étend le schéma V1 sans rien supprimer :
--   * leçons découpées en blocs (explication, définition, code commenté, démonstration,
--     question interactive, exercice) stockés en JSONB ;
--   * chapitres (modules) classés par niveau ;
--   * banque de questions rattachée au parcours / chapitre / leçon, avec difficulté ;
--   * tentatives de quiz (chapitre, examen de parcours, entraînement, erreurs, examen blanc) ;
--   * statistiques par question pour le mode « Mes erreurs » et le suivi des erreurs récurrentes.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Parcours, chapitres, leçons
-- ---------------------------------------------------------------------
ALTER TABLE courses
    ADD COLUMN content_version         INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN exam_question_count     INTEGER NOT NULL DEFAULT 40 CHECK (exam_question_count BETWEEN 30 AND 50),
    ADD COLUMN exam_time_limit_minutes INTEGER CHECK (exam_time_limit_minutes IS NULL
                                                      OR exam_time_limit_minutes BETWEEN 5 AND 240);

ALTER TABLE modules
    ADD COLUMN level               VARCHAR(15) NOT NULL DEFAULT 'DEBUTANT'
                                   CHECK (level IN ('DEBUTANT', 'INTERMEDIAIRE', 'AVANCE', 'EXAMEN')),
    ADD COLUMN quiz_question_count INTEGER     NOT NULL DEFAULT 15 CHECK (quiz_question_count BETWEEN 10 AND 20);

-- Le contenu d'une leçon est désormais une suite de blocs ; les anciennes colonnes texte deviennent facultatives.
ALTER TABLE lessons
    ADD COLUMN blocks    JSONB   NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN xp_reward INTEGER NOT NULL DEFAULT 10 CHECK (xp_reward BETWEEN 0 AND 200),
    ALTER COLUMN prerequisites   DROP NOT NULL,
    ALTER COLUMN course_content  DROP NOT NULL,
    ALTER COLUMN example         DROP NOT NULL,
    ALTER COLUMN demonstration   DROP NOT NULL,
    ALTER COLUMN common_mistakes DROP NOT NULL,
    ALTER COLUMN memo            DROP NOT NULL,
    ALTER COLUMN challenge       DROP NOT NULL;

-- current_step désigne l'index du bloc atteint : une leçon peut compter plus de 16 blocs.
ALTER TABLE progress DROP CONSTRAINT progress_current_step_check;
ALTER TABLE progress ADD CONSTRAINT progress_current_step_check CHECK (current_step >= 0);

-- ---------------------------------------------------------------------
-- Exercices : rattachement direct au parcours (exercices de modélisation hors leçon)
-- ---------------------------------------------------------------------
ALTER TABLE exercises
    ADD COLUMN course_id BIGINT  REFERENCES courses (id) ON DELETE CASCADE,
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX ix_exercises_course ON exercises (course_id);

-- État d'un exercice pour une apprenante : indices dévoilés, correction consultée, brouillon, réussite
CREATE TABLE exercise_states (
    user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    exercise_id      BIGINT       NOT NULL REFERENCES exercises (id) ON DELETE CASCADE,
    hints_used       INTEGER      NOT NULL DEFAULT 0 CHECK (hints_used BETWEEN 0 AND 3),
    solution_viewed  BOOLEAN      NOT NULL DEFAULT FALSE,
    submissions      INTEGER      NOT NULL DEFAULT 0 CHECK (submissions >= 0),
    last_answer      TEXT,
    solved_at        TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, exercise_id)
);

-- ---------------------------------------------------------------------
-- Banque de questions
-- ---------------------------------------------------------------------
ALTER TABLE questions DROP CONSTRAINT questions_kind_check;
ALTER TABLE questions ADD CONSTRAINT questions_kind_check CHECK (kind IN (
    'CHOIX_UNIQUE', 'CHOIX_MULTIPLE', 'VRAI_FAUX', 'TEXTE', 'ORAL', 'COMPLETER_CODE', 'RESULTAT_CODE'));

ALTER TABLE questions
    ADD COLUMN code          VARCHAR(120) UNIQUE,                                   -- identifiant stable (fichiers de contenu)
    ADD COLUMN course_id     BIGINT       REFERENCES courses (id) ON DELETE CASCADE,
    ADD COLUMN module_id     BIGINT       REFERENCES modules (id) ON DELETE CASCADE,
    ADD COLUMN lesson_id     BIGINT       REFERENCES lessons (id) ON DELETE SET NULL, -- question posée pendant la leçon
    ADD COLUMN difficulty    SMALLINT     NOT NULL DEFAULT 1 CHECK (difficulty BETWEEN 1 AND 4),
    ADD COLUMN code_snippet  TEXT,
    ADD COLUMN code_language VARCHAR(20),
    ADD COLUMN published     BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ADD COLUMN updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now();
CREATE INDEX ix_questions_course ON questions (course_id);
CREATE INDEX ix_questions_module ON questions (module_id);
CREATE INDEX ix_questions_lesson ON questions (lesson_id);
CREATE INDEX ix_questions_skill ON questions (skill_id);

-- ---------------------------------------------------------------------
-- Quiz : chapitre, examen de parcours, entraînement, erreurs, aléatoire, examen blanc
-- ---------------------------------------------------------------------
CREATE TABLE quiz_attempts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope               VARCHAR(15)  NOT NULL CHECK (scope IN (
                            'CHAPITRE', 'PARCOURS', 'ENTRAINEMENT', 'ERREURS', 'ALEATOIRE', 'EXAMEN_BLANC')),
    mode                VARCHAR(15)  NOT NULL CHECK (mode IN ('ENTRAINEMENT', 'EXAMEN')),
    course_id           BIGINT       REFERENCES courses (id) ON DELETE CASCADE,
    module_id           BIGINT       REFERENCES modules (id) ON DELETE CASCADE,
    exam_id             BIGINT       REFERENCES exams (id) ON DELETE CASCADE,
    difficulty          SMALLINT     CHECK (difficulty BETWEEN 1 AND 4),
    question_count      INTEGER      NOT NULL CHECK (question_count BETWEEN 1 AND 100),
    time_limit_seconds  INTEGER      CHECK (time_limit_seconds IS NULL OR time_limit_seconds BETWEEN 60 AND 14400),
    started_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deadline_at         TIMESTAMPTZ,
    submitted_at        TIMESTAMPTZ,
    correct_count       INTEGER      NOT NULL DEFAULT 0 CHECK (correct_count >= 0),
    percent             INTEGER      CHECK (percent BETWEEN 0 AND 100),
    passed              BOOLEAN,
    xp_earned           INTEGER      NOT NULL DEFAULT 0 CHECK (xp_earned >= 0)
);
CREATE INDEX ix_quiz_attempts_user ON quiz_attempts (user_id, started_at DESC);
CREATE INDEX ix_quiz_attempts_module ON quiz_attempts (user_id, module_id);
CREATE INDEX ix_quiz_attempts_course ON quiz_attempts (user_id, course_id, scope);

CREATE TABLE quiz_attempt_items (
    attempt_id    BIGINT       NOT NULL REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    position      INTEGER      NOT NULL CHECK (position >= 1),
    question_id   BIGINT       NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    choice_order  INTEGER[]    NOT NULL DEFAULT '{}',   -- ordre d'affichage des choix (positions d'origine)
    given_answer  JSONB,                                -- positions choisies ou texte saisi
    correct       BOOLEAN,
    answered_at   TIMESTAMPTZ,
    PRIMARY KEY (attempt_id, position)
);
CREATE INDEX ix_quiz_attempt_items_question ON quiz_attempt_items (question_id);

-- Historique par question : alimente « Mes erreurs », les erreurs récurrentes et les statistiques par thème
CREATE TABLE user_question_stats (
    user_id              BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id          BIGINT       NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    times_answered       INTEGER      NOT NULL DEFAULT 0 CHECK (times_answered >= 0),
    times_correct        INTEGER      NOT NULL DEFAULT 0 CHECK (times_correct >= 0),
    times_wrong          INTEGER      NOT NULL DEFAULT 0 CHECK (times_wrong >= 0),
    consecutive_correct  INTEGER      NOT NULL DEFAULT 0 CHECK (consecutive_correct >= 0),
    last_answered_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_wrong_at        TIMESTAMPTZ,
    PRIMARY KEY (user_id, question_id)
);
CREATE INDEX ix_user_question_stats_wrong ON user_question_stats (user_id, times_wrong DESC);

-- ---------------------------------------------------------------------
-- Projets de fin de parcours et examens blancs
-- ---------------------------------------------------------------------
ALTER TABLE projects ADD COLUMN course_id BIGINT REFERENCES courses (id) ON DELETE SET NULL;
ALTER TABLE project_steps ADD COLUMN exercise_id BIGINT REFERENCES exercises (id) ON DELETE SET NULL;

ALTER TABLE exams
    ADD COLUMN question_count INTEGER CHECK (question_count IS NULL OR question_count BETWEEN 5 AND 100),
    ADD COLUMN content_version INTEGER NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- Badges supplémentaires liés aux quiz et aux parcours
-- ---------------------------------------------------------------------
ALTER TABLE badges DROP CONSTRAINT badges_criteria_type_check;
ALTER TABLE badges ADD CONSTRAINT badges_criteria_type_check CHECK (criteria_type IN (
    'LECONS_TERMINEES', 'XP_TOTAL', 'SERIE_JOURS', 'EXERCICES_REUSSIS', 'REQUETES_SQL', 'MCD_CREES',
    'EXAMENS_REUSSIS', 'PROJETS_TERMINES', 'REVISIONS', 'QUIZ_REUSSIS', 'CHAPITRES_VALIDES', 'PARCOURS_TERMINES'));

INSERT INTO badges (code, name, description, icon, criteria_type, threshold) VALUES
    ('chapitre-1',   'Chapitre bouclé',    'Valider un premier QCM de fin de chapitre (80 % ou plus).', 'check',   'CHAPITRES_VALIDES', 1),
    ('chapitre-10',  'Bibliothèque vivante', 'Valider 10 chapitres.',                                   'library', 'CHAPITRES_VALIDES', 10),
    ('quiz-10',      'Réflexes affûtés',   'Réussir 10 quiz, tous modes confondus.',                    'sparkle', 'QUIZ_REUSSIS', 10),
    ('quiz-50',      'Machine à QCM',      'Réussir 50 quiz.',                                          'trophy',  'QUIZ_REUSSIS', 50),
    ('parcours-1',   'Parcours complet',   'Réussir l''examen final d''un parcours.',                   'flag',    'PARCOURS_TERMINES', 1),
    ('exercices-10', 'Mains dans le code', 'Réussir 10 exercices pratiques.',                          'code',    'EXERCICES_REUSSIS', 10);
