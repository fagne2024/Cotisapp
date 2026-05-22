-- Options avancées de répartition à la clôture (idempotent).

SET @db = DATABASE();

SET @ddl_agreg = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'parametrage_cloture_exercice' AND COLUMN_NAME = 'mode_agregation_postes') = 0,
    'ALTER TABLE parametrage_cloture_exercice ADD COLUMN mode_agregation_postes VARCHAR(20) NOT NULL DEFAULT ''SEPARER'' AFTER mode_repartition',
    'SELECT 1');
PREPARE stmt_agreg FROM @ddl_agreg;
EXECUTE stmt_agreg;
DEALLOCATE PREPARE stmt_agreg;

SET @ddl_prorata = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'parametrage_cloture_exercice' AND COLUMN_NAME = 'mode_calcul_prorata') = 0,
    'ALTER TABLE parametrage_cloture_exercice ADD COLUMN mode_calcul_prorata VARCHAR(20) NOT NULL DEFAULT ''PARTS'' AFTER mode_agregation_postes',
    'SELECT 1');
PREPARE stmt_prorata FROM @ddl_prorata;
EXECUTE stmt_prorata;
DEALLOCATE PREPARE stmt_prorata;

SET @ddl_pct = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'parametrage_cloture_exercice' AND COLUMN_NAME = 'pourcentages_repartition_json') = 0,
    'ALTER TABLE parametrage_cloture_exercice ADD COLUMN pourcentages_repartition_json TEXT NULL AFTER mode_calcul_prorata',
    'SELECT 1');
PREPARE stmt_pct FROM @ddl_pct;
EXECUTE stmt_pct;
DEALLOCATE PREPARE stmt_pct;

SET @ddl_excl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'parametrage_cloture_exercice' AND COLUMN_NAME = 'exclure_membres_pret_en_cours') = 0,
    'ALTER TABLE parametrage_cloture_exercice ADD COLUMN exclure_membres_pret_en_cours BOOLEAN NOT NULL DEFAULT FALSE AFTER pourcentages_repartition_json',
    'SELECT 1');
PREPARE stmt_excl FROM @ddl_excl;
EXECUTE stmt_excl;
DEALLOCATE PREPARE stmt_excl;
