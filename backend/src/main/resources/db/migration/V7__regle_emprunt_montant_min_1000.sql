-- Corriger les montants minimum erronés à 100 000 FCFA (emprunts)
UPDATE regle_operation
SET montant_min = 1000
WHERE type_operation = 'EMPRUNT'
  AND montant_min = 100000;

UPDATE regle_operation
SET montant_echeance_min = 1000
WHERE type_operation = 'EMPRUNT'
  AND montant_echeance_min = 100000;
