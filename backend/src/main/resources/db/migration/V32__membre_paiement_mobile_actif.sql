-- Paiement mobile money (cotisations / remboursements « Mon compte ») activé par l'admin GIE par membre.
ALTER TABLE membre
    ADD COLUMN paiement_mobile_actif BOOLEAN NOT NULL DEFAULT FALSE;
