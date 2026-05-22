ALTER TABLE operation
    ADD COLUMN mode_paiement VARCHAR(30) NULL AFTER observation,
    ADD COLUMN reference_paiement VARCHAR(120) NULL AFTER mode_paiement;
