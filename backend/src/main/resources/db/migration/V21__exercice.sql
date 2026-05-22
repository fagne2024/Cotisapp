CREATE TABLE IF NOT EXISTS exercice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    numero INT NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_COURS',
    date_debut DATE NOT NULL,
    date_cloture DATE NULL,
    planad_fin INT NULL,
    reinitialisation_comptes BOOLEAN NOT NULL DEFAULT FALSE,
    observation_cloture VARCHAR(500) NULL,
    date_creation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exercice_org FOREIGN KEY (organisation_id) REFERENCES organisation (id),
    CONSTRAINT uk_exercice_org_numero UNIQUE (organisation_id, numero)
);

-- Colonne organisation (sans FK d'abord, pour éviter les cycles à l'init)
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'organisation' AND COLUMN_NAME = 'exercice_courant_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE organisation ADD COLUMN exercice_courant_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'journee_reunion' AND COLUMN_NAME = 'exercice_id');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE journee_reunion ADD COLUMN exercice_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation' AND COLUMN_NAME = 'exercice_id');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE operation ADD COLUMN exercice_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'emprunt' AND COLUMN_NAME = 'exercice_id');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE emprunt ADD COLUMN exercice_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suivi_mensuel' AND COLUMN_NAME = 'exercice_id');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE suivi_mensuel ADD COLUMN exercice_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO exercice (organisation_id, numero, statut, date_debut)
SELECT o.id, 1, 'EN_COURS', COALESCE(DATE(o.date_creation), CURDATE())
FROM organisation o
WHERE NOT EXISTS (
    SELECT 1 FROM exercice e WHERE e.organisation_id = o.id AND e.numero = 1
);

UPDATE organisation o
INNER JOIN exercice e ON e.organisation_id = o.id AND e.numero = 1
SET o.exercice_courant_id = e.id;

UPDATE journee_reunion j
INNER JOIN exercice e ON e.organisation_id = j.organisation_id AND e.numero = 1
SET j.exercice_id = e.id
WHERE j.exercice_id IS NULL;

UPDATE operation op
INNER JOIN exercice e ON e.organisation_id = op.organisation_id AND e.numero = 1
SET op.exercice_id = e.id
WHERE op.exercice_id IS NULL;

UPDATE emprunt emp
INNER JOIN exercice e ON e.organisation_id = emp.organisation_id AND e.numero = 1
SET emp.exercice_id = e.id
WHERE emp.exercice_id IS NULL;

UPDATE suivi_mensuel s
INNER JOIN exercice e ON e.organisation_id = s.organisation_id AND e.numero = 1
SET s.exercice_id = e.id
WHERE s.exercice_id IS NULL;

-- Supprimer les lignes orphelines sans exercice (données incohérentes)
DELETE FROM journee_reunion WHERE exercice_id IS NULL;
DELETE FROM operation WHERE exercice_id IS NULL;
DELETE FROM emprunt WHERE exercice_id IS NULL;
DELETE FROM suivi_mensuel WHERE exercice_id IS NULL;

ALTER TABLE journee_reunion MODIFY exercice_id BIGINT NOT NULL;
ALTER TABLE operation MODIFY exercice_id BIGINT NOT NULL;
ALTER TABLE emprunt MODIFY exercice_id BIGINT NOT NULL;
ALTER TABLE suivi_mensuel MODIFY exercice_id BIGINT NOT NULL;

SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'organisation' AND CONSTRAINT_NAME = 'fk_org_exercice_courant'
);
SET @sql := IF(@fk_exists = 0,
    'ALTER TABLE organisation ADD CONSTRAINT fk_org_exercice_courant FOREIGN KEY (exercice_courant_id) REFERENCES exercice (id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'journee_reunion' AND CONSTRAINT_NAME = 'fk_journee_exercice'
);
SET @sql := IF(@fk_exists = 0,
    'ALTER TABLE journee_reunion ADD CONSTRAINT fk_journee_exercice FOREIGN KEY (exercice_id) REFERENCES exercice (id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation' AND CONSTRAINT_NAME = 'fk_operation_exercice'
);
SET @sql := IF(@fk_exists = 0,
    'ALTER TABLE operation ADD CONSTRAINT fk_operation_exercice FOREIGN KEY (exercice_id) REFERENCES exercice (id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'emprunt' AND CONSTRAINT_NAME = 'fk_emprunt_exercice'
);
SET @sql := IF(@fk_exists = 0,
    'ALTER TABLE emprunt ADD CONSTRAINT fk_emprunt_exercice FOREIGN KEY (exercice_id) REFERENCES exercice (id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suivi_mensuel' AND CONSTRAINT_NAME = 'fk_suivi_exercice'
);
SET @sql := IF(@fk_exists = 0,
    'ALTER TABLE suivi_mensuel ADD CONSTRAINT fk_suivi_exercice FOREIGN KEY (exercice_id) REFERENCES exercice (id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'journee_reunion' AND INDEX_NAME = 'uk_journee_org_date'
);
SET @sql := IF(@idx_exists > 0, 'ALTER TABLE journee_reunion DROP INDEX uk_journee_org_date', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'journee_reunion' AND INDEX_NAME = 'uk_journee_org_numero'
);
SET @sql := IF(@idx_exists > 0, 'ALTER TABLE journee_reunion DROP INDEX uk_journee_org_numero', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @uk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'journee_reunion' AND CONSTRAINT_NAME = 'uk_journee_exercice_date'
);
SET @sql := IF(@uk_exists = 0,
    'ALTER TABLE journee_reunion ADD CONSTRAINT uk_journee_exercice_date UNIQUE (exercice_id, date_reunion)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @uk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'journee_reunion' AND CONSTRAINT_NAME = 'uk_journee_exercice_numero'
);
SET @sql := IF(@uk_exists = 0,
    'ALTER TABLE journee_reunion ADD CONSTRAINT uk_journee_exercice_numero UNIQUE (exercice_id, numero)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index sur membre_id pour que fk_suivi_membre survive au remplacement de l'unicité
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suivi_mensuel' AND INDEX_NAME = 'idx_suivi_membre_fk'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_suivi_membre_fk ON suivi_mensuel (membre_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @old_uk := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suivi_mensuel' AND CONSTRAINT_NAME = 'uk_suivi_membre_mois'
);
SET @new_uk := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suivi_mensuel' AND CONSTRAINT_NAME = 'uk_suivi_exercice_membre_mois'
);
SET @sql := IF(@old_uk > 0 AND @new_uk = 0,
    'ALTER TABLE suivi_mensuel DROP INDEX uk_suivi_membre_mois, ADD CONSTRAINT uk_suivi_exercice_membre_mois UNIQUE (exercice_id, membre_id, mois_annee)',
    IF(@new_uk = 0 AND @old_uk = 0,
        'ALTER TABLE suivi_mensuel ADD CONSTRAINT uk_suivi_exercice_membre_mois UNIQUE (exercice_id, membre_id, mois_annee)',
        'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'journee_reunion' AND INDEX_NAME = 'idx_journee_exercice'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_journee_exercice ON journee_reunion (exercice_id, numero DESC)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation' AND INDEX_NAME = 'idx_operation_exercice'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_operation_exercice ON operation (exercice_id, date_operation DESC)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'emprunt' AND INDEX_NAME = 'idx_emprunt_exercice'
);
SET @sql := IF(@idx_exists = 0, 'CREATE INDEX idx_emprunt_exercice ON emprunt (exercice_id, statut)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
