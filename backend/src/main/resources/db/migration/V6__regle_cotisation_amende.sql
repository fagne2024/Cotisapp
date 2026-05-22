-- Plage amende applicable sur les règles de cotisation
ALTER TABLE regle_operation
    ADD COLUMN montant_amende_min DECIMAL(19, 2) NULL,
    ADD COLUMN montant_amende_max DECIMAL(19, 2) NULL;

UPDATE regle_operation
SET montant_amende_min = 500,
    montant_amende_max = 3000
WHERE type_operation = 'COTISATION'
  AND montant_amende_min IS NULL;

UPDATE regle_operation
SET montant_amende_min = 1000,
    montant_amende_max = 5000
WHERE type_operation = 'COTISATION_MOIS'
  AND montant_amende_min IS NULL;
