ALTER TABLE regle_operation
    ADD COLUMN jours_alerte_echeance_proche INT NULL;

UPDATE regle_operation
SET jours_alerte_echeance_proche = 7
WHERE type_operation = 'EMPRUNT'
  AND jours_alerte_echeance_proche IS NULL;
