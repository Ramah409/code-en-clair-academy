-- ---------------------------------------------------------------------
-- Code en Clair Academy — V7 : origine des réponses de l'assistant
-- ---------------------------------------------------------------------
-- OLLAMA : réponse du modèle local ; COURS : réponse de secours tirée des contenus (sans IA)
ALTER TABLE tutor_messages
    ADD COLUMN source VARCHAR(10) CHECK (source IS NULL OR source IN ('OLLAMA', 'COURS')),
    ADD COLUMN links  JSONB NOT NULL DEFAULT '[]'::jsonb;
