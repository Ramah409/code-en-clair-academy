-- =====================================================================
-- CDA Academy — V5 : le laboratoire peut déclarer des clés étrangères vers ses tables pédagogiques
-- (exercices de création de tables, par ex. REFERENCES clients (id)). Les modifications restent
-- toujours annulées en fin d'exécution.
-- =====================================================================
GRANT REFERENCES ON ALL TABLES IN SCHEMA lab TO cda_lab;
