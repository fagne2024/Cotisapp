-- Idempotent : tolère une exécution partielle précédente (repair + migrate).

SET @db = DATABASE();

SET @ddl_mode = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'parametrage_cloture_exercice' AND COLUMN_NAME = 'mode_repartition') = 0,
    'ALTER TABLE parametrage_cloture_exercice ADD COLUMN mode_repartition VARCHAR(20) NOT NULL DEFAULT ''PRORATA'' AFTER partager_amendes',
    'SELECT 1');
PREPARE stmt_mode FROM @ddl_mode;
EXECUTE stmt_mode;
DEALLOCATE PREPARE stmt_mode;

SET @ddl_postes = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'parametrage_cloture_exercice' AND COLUMN_NAME = 'postes_partage_json') = 0,
    'ALTER TABLE parametrage_cloture_exercice ADD COLUMN postes_partage_json TEXT NULL AFTER retenues_json',
    'SELECT 1');
PREPARE stmt_postes FROM @ddl_postes;
EXECUTE stmt_postes;
DEALLOCATE PREPARE stmt_postes;

-- Compte organisation « Intérêts » (regroupe les frais/intérêts d'emprunt)
INSERT INTO parametrage_compte_organisation (organisation_id, famille, libelle, type_compte, proprietaire, actif)
SELECT o.id, 'INTERET', 'Compte intérêts', 'INTERET', 'ORGANISATION', TRUE
FROM organisation o
WHERE NOT EXISTS (
    SELECT 1 FROM parametrage_compte_organisation p
    WHERE p.organisation_id = o.id AND p.famille = 'INTERET'
);

INSERT INTO compte (organisation_id, membre_id, type_compte, proprietaire, solde, libelle)
SELECT p.organisation_id, NULL, 'INTERET', 'ORGANISATION', 0, p.libelle
FROM parametrage_compte_organisation p
WHERE p.famille = 'INTERET' AND p.proprietaire = 'ORGANISATION'
  AND NOT EXISTS (
      SELECT 1 FROM compte c
      WHERE c.organisation_id = p.organisation_id
        AND c.type_compte = 'INTERET'
        AND c.proprietaire = 'ORGANISATION'
  );
