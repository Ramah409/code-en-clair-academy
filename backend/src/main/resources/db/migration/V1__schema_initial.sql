-- =====================================================================
-- CDA Academy — V1 : schéma initial
-- Toutes les tables du modèle de données (voir docs/conception/04-dictionnaire-de-donnees.md)
-- Conventions : snake_case, clés techniques BIGINT IDENTITY, horodatage TIMESTAMPTZ,
-- énumérations contrôlées par CHECK, suppression en cascade des données personnelles.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Comptes et sécurité
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE CHECK (code IN ('USER', 'ADMIN')),
    label       VARCHAR(60)  NOT NULL
);

CREATE TABLE users (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email                VARCHAR(254) NOT NULL,
    display_name         VARCHAR(60)  NOT NULL CHECK (char_length(trim(display_name)) >= 2),
    password_hash        VARCHAR(100) NOT NULL,
    role_id              BIGINT       NOT NULL REFERENCES roles (id),
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    xp                   INTEGER      NOT NULL DEFAULT 0 CHECK (xp >= 0),
    current_streak       INTEGER      NOT NULL DEFAULT 0 CHECK (current_streak >= 0),
    longest_streak       INTEGER      NOT NULL DEFAULT 0 CHECK (longest_streak >= 0),
    last_activity_date   DATE,
    daily_goal_minutes   INTEGER      NOT NULL DEFAULT 20 CHECK (daily_goal_minutes BETWEEN 5 AND 240),
    theme                VARCHAR(10)  NOT NULL DEFAULT 'SYSTEM' CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM')),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Unicité insensible à la casse de l'adresse e-mail
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

CREATE TABLE refresh_tokens (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256 hexadécimal, le jeton brut n'est jamais stocké
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

-- Paramètres applicatifs modifiables par l'administratrice (modèle Ollama, etc.)
CREATE TABLE app_settings (
    setting_key    VARCHAR(80)  PRIMARY KEY,
    setting_value  VARCHAR(500) NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Contenus pédagogiques
-- ---------------------------------------------------------------------
CREATE TABLE skills (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(60)  NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    category    VARCHAR(40)  NOT NULL CHECK (category IN (
                    'WEB', 'ALGO', 'DONNEES', 'SQL', 'JAVA', 'SPRING', 'ANGULAR',
                    'API', 'SECURITE', 'QUALITE', 'PROJET')),
    description TEXT
);

CREATE TABLE courses (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug         VARCHAR(80)  NOT NULL UNIQUE,
    level_number INTEGER      NOT NULL UNIQUE CHECK (level_number BETWEEN 1 AND 20),
    title        VARCHAR(150) NOT NULL,
    summary      VARCHAR(500) NOT NULL,
    description  TEXT         NOT NULL,
    category     VARCHAR(40)  NOT NULL,
    icon         VARCHAR(40)  NOT NULL DEFAULT 'book',
    published    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE modules (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id   BIGINT       NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    slug        VARCHAR(80)  NOT NULL,
    title       VARCHAR(150) NOT NULL,
    summary     VARCHAR(500) NOT NULL,
    position    INTEGER      NOT NULL CHECK (position >= 1),
    CONSTRAINT ux_modules_course_slug UNIQUE (course_id, slug),
    CONSTRAINT ux_modules_course_position UNIQUE (course_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE lessons (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    module_id        BIGINT       NOT NULL REFERENCES modules (id) ON DELETE CASCADE,
    slug             VARCHAR(100) NOT NULL UNIQUE,
    title            VARCHAR(150) NOT NULL,
    position         INTEGER      NOT NULL CHECK (position >= 1),
    duration_minutes INTEGER      NOT NULL CHECK (duration_minutes BETWEEN 5 AND 15),
    prerequisites    TEXT         NOT NULL,
    objective        TEXT         NOT NULL,
    course_content   TEXT         NOT NULL,   -- Markdown
    example          TEXT         NOT NULL,
    demonstration    TEXT         NOT NULL,
    common_mistakes  TEXT         NOT NULL,
    memo             TEXT         NOT NULL,
    challenge        TEXT         NOT NULL,
    is_preview       BOOLEAN      NOT NULL DEFAULT FALSE,  -- leçon testable par un visiteur
    published        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_lessons_module_position UNIQUE (module_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE lesson_skills (
    lesson_id  BIGINT NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    skill_id   BIGINT NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    PRIMARY KEY (lesson_id, skill_id)
);

CREATE TABLE exercises (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lesson_id           BIGINT       REFERENCES lessons (id) ON DELETE CASCADE,
    skill_id            BIGINT       REFERENCES skills (id) ON DELETE SET NULL,
    slug                VARCHAR(120) NOT NULL UNIQUE,
    kind                VARCHAR(20)  NOT NULL CHECK (kind IN (
                            'QCM', 'VRAI_FAUX', 'COMPLETER', 'REMETTRE_ORDRE', 'CORRIGER_ERREUR',
                            'CODE_LIBRE', 'SQL', 'MCD', 'MCD_VERS_MLD', 'MLD_VERS_SQL',
                            'POSTMAN', 'ORAL')),
    purpose             VARCHAR(20)  NOT NULL CHECK (purpose IN (
                            'EXERCICE', 'QUIZ', 'MINI_DEFI', 'VALIDATION', 'LIBRE')),
    difficulty          VARCHAR(15)  NOT NULL CHECK (difficulty IN ('FACILE', 'INTERMEDIAIRE', 'DIFFICILE')),
    position            INTEGER      NOT NULL DEFAULT 1 CHECK (position >= 1),
    title               VARCHAR(150) NOT NULL,
    statement           TEXT         NOT NULL,
    success_criteria    TEXT         NOT NULL,
    hint_1              TEXT         NOT NULL,
    hint_2              TEXT         NOT NULL,
    hint_3              TEXT         NOT NULL,
    solution            TEXT         NOT NULL,
    explanation         TEXT         NOT NULL,
    time_limit_seconds  INTEGER      CHECK (time_limit_seconds IS NULL OR time_limit_seconds BETWEEN 30 AND 7200),
    xp_reward           INTEGER      NOT NULL DEFAULT 10 CHECK (xp_reward BETWEEN 0 AND 500),
    payload             JSONB        NOT NULL DEFAULT '{}'::jsonb,  -- données propres au type (schéma SQL, résultat attendu, lignes à ordonner...)
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_exercises_lesson ON exercises (lesson_id);
CREATE INDEX ix_exercises_skill ON exercises (skill_id);

-- Question : appartient à un exercice (QCM, quiz) ou à la banque d'examen (exercise_id NULL)
CREATE TABLE questions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exercise_id      BIGINT       REFERENCES exercises (id) ON DELETE CASCADE,
    skill_id         BIGINT       REFERENCES skills (id) ON DELETE SET NULL,
    position         INTEGER      NOT NULL DEFAULT 1 CHECK (position >= 1),
    kind             VARCHAR(20)  NOT NULL CHECK (kind IN ('CHOIX_UNIQUE', 'CHOIX_MULTIPLE', 'VRAI_FAUX', 'TEXTE', 'ORAL')),
    prompt           TEXT         NOT NULL,
    expected_answer  TEXT,          -- réponse attendue (TEXTE, ORAL) ou mots-clés
    explanation      TEXT         NOT NULL
);
CREATE INDEX ix_questions_exercise ON questions (exercise_id);

CREATE TABLE choices (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id  BIGINT       NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    position     INTEGER      NOT NULL CHECK (position >= 1),
    label        TEXT         NOT NULL,
    correct      BOOLEAN      NOT NULL DEFAULT FALSE,
    explanation  TEXT         NOT NULL,  -- pourquoi ce choix est juste ou faux
    CONSTRAINT ux_choices_question_position UNIQUE (question_id, position)
);

-- ---------------------------------------------------------------------
-- Suivi de l'apprenante
-- ---------------------------------------------------------------------
CREATE TABLE attempts (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    exercise_id       BIGINT        NOT NULL REFERENCES exercises (id) ON DELETE CASCADE,
    started_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    submitted_at      TIMESTAMPTZ,
    score             NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (score >= 0),
    max_score         NUMERIC(5, 2) NOT NULL DEFAULT 1 CHECK (max_score > 0),
    success           BOOLEAN       NOT NULL DEFAULT FALSE,
    hints_used        INTEGER       NOT NULL DEFAULT 0 CHECK (hints_used BETWEEN 0 AND 3),
    duration_seconds  INTEGER       CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    CONSTRAINT ck_attempts_score CHECK (score <= max_score)
);
CREATE INDEX ix_attempts_user_exercise ON attempts (user_id, exercise_id);
CREATE INDEX ix_attempts_user_date ON attempts (user_id, started_at DESC);

CREATE TABLE answers (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id    BIGINT   NOT NULL REFERENCES attempts (id) ON DELETE CASCADE,
    question_id   BIGINT   REFERENCES questions (id) ON DELETE SET NULL,
    given_answer  TEXT     NOT NULL,
    correct       BOOLEAN  NOT NULL,
    feedback      TEXT     NOT NULL
);
CREATE INDEX ix_answers_attempt ON answers (attempt_id);

CREATE TABLE progress (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    lesson_id           BIGINT       NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    status              VARCHAR(15)  NOT NULL DEFAULT 'EN_COURS' CHECK (status IN ('EN_COURS', 'TERMINEE')),
    current_step        INTEGER      NOT NULL DEFAULT 1 CHECK (current_step BETWEEN 1 AND 16),
    best_score          INTEGER      NOT NULL DEFAULT 0 CHECK (best_score BETWEEN 0 AND 100),
    time_spent_seconds  INTEGER      NOT NULL DEFAULT 0 CHECK (time_spent_seconds >= 0),
    started_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT ux_progress_user_lesson UNIQUE (user_id, lesson_id),
    CONSTRAINT ck_progress_completed CHECK ((status = 'TERMINEE') = (completed_at IS NOT NULL))
);

CREATE TABLE user_skills (
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    skill_id    BIGINT       NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    mastery     INTEGER      NOT NULL DEFAULT 0 CHECK (mastery BETWEEN 0 AND 100),
    status      VARCHAR(15)  NOT NULL DEFAULT 'EN_COURS' CHECK (status IN ('A_REVOIR', 'EN_COURS', 'MAITRISEE')),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, skill_id)
);

CREATE TABLE badges (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(60)  NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(300) NOT NULL,
    icon            VARCHAR(40)  NOT NULL,
    criteria_type   VARCHAR(30)  NOT NULL CHECK (criteria_type IN (
                        'LECONS_TERMINEES', 'XP_TOTAL', 'SERIE_JOURS', 'EXERCICES_REUSSIS',
                        'REQUETES_SQL', 'MCD_CREES', 'EXAMENS_REUSSIS', 'PROJETS_TERMINES', 'REVISIONS')),
    threshold       INTEGER      NOT NULL CHECK (threshold > 0)
);

CREATE TABLE user_badges (
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    badge_id   BIGINT       NOT NULL REFERENCES badges (id) ON DELETE CASCADE,
    earned_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, badge_id)
);

-- Répétition espacée (algorithme SM-2 simplifié)
CREATE TABLE revisions (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    exercise_id       BIGINT        NOT NULL REFERENCES exercises (id) ON DELETE CASCADE,
    ease_factor       NUMERIC(4, 2) NOT NULL DEFAULT 2.50 CHECK (ease_factor BETWEEN 1.30 AND 3.00),
    interval_days     INTEGER       NOT NULL DEFAULT 1 CHECK (interval_days >= 1),
    repetitions       INTEGER       NOT NULL DEFAULT 0 CHECK (repetitions >= 0),
    lapses            INTEGER       NOT NULL DEFAULT 0 CHECK (lapses >= 0),
    due_date          DATE          NOT NULL,
    last_reviewed_at  TIMESTAMPTZ,
    CONSTRAINT ux_revisions_user_exercise UNIQUE (user_id, exercise_id)
);
CREATE INDEX ix_revisions_due ON revisions (user_id, due_date);

-- Temps de travail et calendrier d'activité (une ligne par jour actif)
CREATE TABLE daily_activity (
    user_id           BIGINT   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    activity_date     DATE     NOT NULL,
    seconds_studied   INTEGER  NOT NULL DEFAULT 0 CHECK (seconds_studied >= 0),
    xp_earned         INTEGER  NOT NULL DEFAULT 0 CHECK (xp_earned >= 0),
    exercises_done    INTEGER  NOT NULL DEFAULT 0 CHECK (exercises_done >= 0),
    PRIMARY KEY (user_id, activity_date)
);

-- ---------------------------------------------------------------------
-- Projets pratiques
-- ---------------------------------------------------------------------
CREATE TABLE projects (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug                 VARCHAR(80)  NOT NULL UNIQUE,
    position             INTEGER      NOT NULL UNIQUE CHECK (position >= 1),
    title                VARCHAR(150) NOT NULL,
    difficulty           VARCHAR(15)  NOT NULL CHECK (difficulty IN ('FACILE', 'INTERMEDIAIRE', 'DIFFICILE')),
    statement            TEXT         NOT NULL,
    needs                TEXT         NOT NULL,
    business_rules       TEXT         NOT NULL,
    user_stories         TEXT         NOT NULL,
    mockup               TEXT         NOT NULL,
    mcd                  TEXT         NOT NULL,
    mld                  TEXT         NOT NULL,
    acceptance_criteria  TEXT         NOT NULL,
    tests                TEXT         NOT NULL,
    correction           TEXT         NOT NULL,
    improvements         TEXT         NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE project_steps (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id    BIGINT       NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    position      INTEGER      NOT NULL CHECK (position >= 1),
    title         VARCHAR(150) NOT NULL,
    instructions  TEXT         NOT NULL,
    deliverable   TEXT         NOT NULL,
    checklist     TEXT         NOT NULL,
    CONSTRAINT ux_project_steps_position UNIQUE (project_id, position)
);

CREATE TABLE user_project_steps (
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    step_id       BIGINT       NOT NULL REFERENCES project_steps (id) ON DELETE CASCADE,
    completed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    notes         TEXT,
    PRIMARY KEY (user_id, step_id)
);

-- ---------------------------------------------------------------------
-- Examens CDA
-- ---------------------------------------------------------------------
CREATE TABLE exams (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug              VARCHAR(80)  NOT NULL UNIQUE,
    kind              VARCHAR(20)  NOT NULL CHECK (kind IN ('EXAMEN_BLANC', 'ETUDE_DE_CAS', 'ORAL')),
    title             VARCHAR(150) NOT NULL,
    description       TEXT         NOT NULL,
    duration_minutes  INTEGER      NOT NULL CHECK (duration_minutes BETWEEN 5 AND 300),
    passing_score     INTEGER      NOT NULL DEFAULT 60 CHECK (passing_score BETWEEN 1 AND 100),
    published         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE exam_questions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exam_id      BIGINT   NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    question_id  BIGINT   NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    position     INTEGER  NOT NULL CHECK (position >= 1),
    points       INTEGER  NOT NULL DEFAULT 1 CHECK (points BETWEEN 1 AND 20),
    CONSTRAINT ux_exam_questions_question UNIQUE (exam_id, question_id),
    CONSTRAINT ux_exam_questions_position UNIQUE (exam_id, position)
);

CREATE TABLE exam_attempts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    exam_id       BIGINT        NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    started_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deadline_at   TIMESTAMPTZ   NOT NULL,
    submitted_at  TIMESTAMPTZ,
    score         NUMERIC(6, 2) NOT NULL DEFAULT 0 CHECK (score >= 0),
    max_score     NUMERIC(6, 2) NOT NULL DEFAULT 1 CHECK (max_score > 0),
    passed        BOOLEAN,
    answers       JSONB         NOT NULL DEFAULT '[]'::jsonb,   -- réponses écrites (enregistrement facultatif)
    feedback      JSONB         NOT NULL DEFAULT '{}'::jsonb,   -- correction détaillée et recommandations
    CONSTRAINT ck_exam_attempts_deadline CHECK (deadline_at > started_at)
);
CREATE INDEX ix_exam_attempts_user ON exam_attempts (user_id, started_at DESC);

-- ---------------------------------------------------------------------
-- Éditeur MCD
-- ---------------------------------------------------------------------
CREATE TABLE mcd_diagrams (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    exercise_id  BIGINT        REFERENCES exercises (id) ON DELETE SET NULL,
    title        VARCHAR(150)  NOT NULL,
    content      JSONB         NOT NULL,   -- entités, attributs, associations, cardinalités, positions
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_mcd_diagrams_user ON mcd_diagrams (user_id);

-- ---------------------------------------------------------------------
-- Tuteur IA local (Ollama)
-- ---------------------------------------------------------------------
CREATE TABLE tutor_conversations (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mode        VARCHAR(20)   NOT NULL CHECK (mode IN (
                    'GENERAL', 'SQL', 'MERISE', 'JAVA', 'SPRING', 'ANGULAR',
                    'SECURITE', 'CORRECTION', 'JURY', 'PLANNING')),
    title       VARCHAR(150)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_tutor_conversations_user ON tutor_conversations (user_id, updated_at DESC);

CREATE TABLE tutor_messages (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id  BIGINT        NOT NULL REFERENCES tutor_conversations (id) ON DELETE CASCADE,
    author           VARCHAR(10)   NOT NULL CHECK (author IN ('USER', 'ASSISTANT')),
    content          TEXT          NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_tutor_messages_conversation ON tutor_messages (conversation_id, created_at);
