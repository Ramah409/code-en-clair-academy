-- ---------------------------------------------------------------------
-- Code en Clair Academy — V6 : espace Examen CDA et attestations
-- ---------------------------------------------------------------------

-- Parcours obligatoires pour la certification globale (un parcours personnel peut être facultatif)
ALTER TABLE courses ADD COLUMN mandatory BOOLEAN NOT NULL DEFAULT TRUE;

-- Examens importés depuis database/content/examens : contenu riche (étude de cas, oral),
-- examen blanc final exigé pour la certification, version du fichier source
ALTER TABLE exams
    ADD COLUMN content         JSONB    NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN final_exam      BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN position        INTEGER  NOT NULL DEFAULT 1,
    ADD COLUMN level           VARCHAR(15) CHECK (level IN ('DEBUTANT', 'INTERMEDIAIRE', 'AVANCE', 'EXAMEN'));

-- Réponses rédigées aux études de cas et auto-évaluation avec la grille de correction
CREATE TABLE case_study_answers (
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    exam_id     BIGINT       NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    answers     JSONB        NOT NULL DEFAULT '{}'::jsonb,   -- { "1": "réponse à la tâche 1", ... }
    checked     JSONB        NOT NULL DEFAULT '{}'::jsonb,   -- { "1": [0, 2], ... } critères cochés
    revealed    BOOLEAN      NOT NULL DEFAULT FALSE,
    completed   BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, exam_id)
);

-- Entraînement aux questions du jury : auto-évaluation « je savais / à revoir »
CREATE TABLE jury_reviews (
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    item_key     VARCHAR(40)  NOT NULL,
    known        BOOLEAN      NOT NULL,
    times        INTEGER      NOT NULL DEFAULT 1 CHECK (times >= 1),
    reviewed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, item_key)
);

-- Attestations : par parcours et par niveau, par parcours complet, et certification globale
CREATE TABLE certificates (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,            -- code public de vérification
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind         VARCHAR(10)  NOT NULL CHECK (kind IN ('NIVEAU', 'PARCOURS', 'GLOBALE')),
    course_id    BIGINT       REFERENCES courses (id) ON DELETE SET NULL,   -- l'attestation survit au parcours
    level        VARCHAR(15)  CHECK (level IN ('DEBUTANT', 'INTERMEDIAIRE', 'AVANCE', 'EXAMEN')),
    title        VARCHAR(200) NOT NULL,
    holder_name  VARCHAR(120) NOT NULL,
    details      JSONB        NOT NULL DEFAULT '{}'::jsonb, -- chapitres validés, scores retenus
    issued_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_certificates_unique UNIQUE NULLS NOT DISTINCT (user_id, kind, course_id, level),
    CONSTRAINT ck_certificates_shape CHECK (
        (kind = 'NIVEAU' AND level IS NOT NULL)
        OR (kind = 'PARCOURS' AND level IS NULL)
        OR (kind = 'GLOBALE' AND course_id IS NULL AND level IS NULL))
);
CREATE INDEX ix_certificates_user ON certificates (user_id, issued_at DESC);
