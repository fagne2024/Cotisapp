-- Suivi de l'avance Caisse lors d'un octroi Solidarité (solde insuffisant)
ALTER TABLE emprunt
    ADD COLUMN montant_avance_caisse DECIMAL(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN montant_rembourse_avance_caisse DECIMAL(19, 2) NOT NULL DEFAULT 0;
