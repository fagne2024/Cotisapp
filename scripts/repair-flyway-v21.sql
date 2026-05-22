-- À exécuter dans la base cotisapp (MySQL Workbench, DBeaver, etc.)
-- si le backend refuse de démarrer : "Detected failed migration to version 21"

DELETE FROM flyway_schema_history
WHERE version = '21' AND success = 0;

-- Puis redémarrer le backend : Flyway réappliquera V21__exercice.sql
