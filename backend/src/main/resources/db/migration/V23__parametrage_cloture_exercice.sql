CREATE TABLE parametrage_cloture_exercice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    cotisation_montant_min DECIMAL(19, 2) NOT NULL DEFAULT 1000,
    cotisation_montant_max DECIMAL(19, 2) NOT NULL DEFAULT 10000,
    parts_min INT NOT NULL DEFAULT 1,
    parts_max INT NOT NULL DEFAULT 10,
    partager_interets BOOLEAN NOT NULL DEFAULT TRUE,
    partager_penalites BOOLEAN NOT NULL DEFAULT TRUE,
    partager_amendes BOOLEAN NOT NULL DEFAULT TRUE,
    frais_cloture_type VARCHAR(20) NOT NULL DEFAULT 'FIXE',
    frais_cloture_valeur DECIMAL(19, 2) NOT NULL DEFAULT 0,
    retenues_json TEXT NULL,
    compte_versement_membre VARCHAR(40) NOT NULL DEFAULT 'EPARGNE_HEBDO',
    compte_source_org VARCHAR(40) NOT NULL DEFAULT 'CAISSE',
    CONSTRAINT uk_pce_org UNIQUE (organisation_id),
    CONSTRAINT fk_pce_org FOREIGN KEY (organisation_id) REFERENCES organisation(id)
);
