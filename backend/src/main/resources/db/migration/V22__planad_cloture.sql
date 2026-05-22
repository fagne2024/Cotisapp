ALTER TABLE journee_reunion
    ADD COLUMN statut VARCHAR(20) NOT NULL DEFAULT 'OUVERT',
    ADD COLUMN date_cloture DATE NULL;

-- Exercices déjà clôturés : tous les PLANAD sont clôturés
UPDATE journee_reunion j
INNER JOIN exercice e ON e.id = j.exercice_id
SET j.statut = 'CLOTURE',
    j.date_cloture = COALESCE(e.date_cloture, CURDATE())
WHERE e.statut = 'CLOTURE';

-- Exercice en cours : seul le dernier numéro reste ouvert
UPDATE journee_reunion j
INNER JOIN (
    SELECT exercice_id, MAX(numero) AS max_numero
    FROM journee_reunion
    GROUP BY exercice_id
) dernier ON dernier.exercice_id = j.exercice_id
INNER JOIN exercice e ON e.id = j.exercice_id AND e.statut = 'EN_COURS'
SET j.statut = IF(j.numero = dernier.max_numero, 'OUVERT', 'CLOTURE'),
    j.date_cloture = IF(j.numero = dernier.max_numero, NULL, CURDATE());
