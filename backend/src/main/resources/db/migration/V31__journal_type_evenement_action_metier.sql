-- Normalise les anciennes valeurs « ACTION » vers l'enum applicatif ACTION_METIER
UPDATE journal_audit SET type_evenement = 'ACTION_METIER'
WHERE type_evenement = 'ACTION' OR type_evenement IS NULL OR type_evenement = '';

ALTER TABLE journal_audit
    MODIFY COLUMN type_evenement VARCHAR(40) NOT NULL DEFAULT 'ACTION_METIER';
