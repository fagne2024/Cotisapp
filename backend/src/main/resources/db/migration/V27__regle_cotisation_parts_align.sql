-- Réaligne parts_min/max sur montant_min/max (multiple de montant_par_part)
UPDATE regle_operation
SET parts_min = GREATEST(1, CAST(FLOOR(montant_min / montant_par_part) AS SIGNED)),
    parts_max = GREATEST(
        GREATEST(1, CAST(FLOOR(montant_min / montant_par_part) AS SIGNED)),
        CAST(FLOOR(montant_max / montant_par_part) AS SIGNED)
    )
WHERE type_operation IN ('COTISATION', 'COTISATION_MOIS')
  AND montant_par_part IS NOT NULL
  AND montant_par_part > 0
  AND montant_min IS NOT NULL
  AND montant_max IS NOT NULL
  AND MOD(montant_min, montant_par_part) = 0
  AND MOD(montant_max, montant_par_part) = 0;
