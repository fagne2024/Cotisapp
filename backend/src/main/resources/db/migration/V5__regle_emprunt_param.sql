-- Paramétrage emprunts sur regle_operation (règles type EMPRUNT)
ALTER TABLE regle_operation
    ADD COLUMN type_frais VARCHAR(20) NULL,
    ADD COLUMN montant_frais DECIMAL(19, 2) NULL,
    ADD COLUMN pourcentage_frais DECIMAL(8, 4) NULL,
    ADD COLUMN nb_echeances_min INT NULL,
    ADD COLUMN nb_echeances_max INT NULL,
    ADD COLUMN nb_echeances_defaut INT NULL,
    ADD COLUMN montant_echeance_min DECIMAL(19, 2) NULL,
    ADD COLUMN montant_echeance_max DECIMAL(19, 2) NULL,
    ADD COLUMN type_penalite VARCHAR(20) NULL,
    ADD COLUMN montant_penalite DECIMAL(19, 2) NULL,
    ADD COLUMN pourcentage_penalite DECIMAL(8, 4) NULL;
