-- Callback Flyway exécuté à chaque démarrage : aligne le mot de passe du rôle du laboratoire
-- sur la variable d'environnement LAB_DB_PASSWORD.
ALTER ROLE cda_lab PASSWORD '${labPassword}';
