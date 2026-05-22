-- Rattrapage des numéros déjà saisis sans telephone_normalise (connexion membre).
UPDATE membre m
SET m.telephone_normalise = (
    SELECT n.tel FROM (
        SELECT id,
               CASE
                   WHEN telephone IS NULL OR TRIM(telephone) = '' THEN NULL
                   WHEN LENGTH(REGEXP_REPLACE(telephone, '[^0-9]', '')) = 9
                        AND LEFT(REGEXP_REPLACE(telephone, '[^0-9]', ''), 1) IN ('7', '3')
                       THEN CONCAT('221', REGEXP_REPLACE(telephone, '[^0-9]', ''))
                   WHEN LEFT(REGEXP_REPLACE(telephone, '[^0-9]', ''), 2) = '00'
                       THEN SUBSTRING(REGEXP_REPLACE(telephone, '[^0-9]', ''), 3)
                   ELSE REGEXP_REPLACE(telephone, '[^0-9]', '')
               END AS tel
        FROM membre
    ) n
    WHERE n.id = m.id
)
WHERE m.telephone IS NOT NULL
  AND TRIM(m.telephone) <> ''
  AND (m.telephone_normalise IS NULL OR TRIM(m.telephone_normalise) = '');

UPDATE utilisateur u
INNER JOIN membre m ON m.utilisateur_id = u.id
SET u.telephone = m.telephone,
    u.telephone_normalise = m.telephone_normalise
WHERE m.telephone_normalise IS NOT NULL
  AND (u.telephone_normalise IS NULL OR TRIM(u.telephone_normalise) = '');
