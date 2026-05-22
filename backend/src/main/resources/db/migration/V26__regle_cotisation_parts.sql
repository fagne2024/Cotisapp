ALTER TABLE regle_operation
    ADD COLUMN montant_par_part DECIMAL(19, 2) NULL,
    ADD COLUMN parts_min INT NULL,
    ADD COLUMN parts_max INT NULL;

UPDATE regle_operation
SET montant_par_part = 1000,
    parts_min = GREATEST(1, CAST(FLOOR(COALESCE(montant_min, 1000) / 1000) AS SIGNED)),
    parts_max = GREATEST(1, CAST(FLOOR(COALESCE(montant_max, 10000) / 1000) AS SIGNED))
WHERE type_operation IN ('COTISATION', 'COTISATION_MOIS');
