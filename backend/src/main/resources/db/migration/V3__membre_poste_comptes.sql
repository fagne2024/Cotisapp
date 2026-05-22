ALTER TABLE membre ADD COLUMN poste VARCHAR(30) NOT NULL DEFAULT 'SIMPLE';
ALTER TABLE membre ADD COLUMN email VARCHAR(255);
ALTER TABLE membre ADD COLUMN date_adhesion DATE;
ALTER TABLE membre ADD COLUMN piece_identite VARCHAR(80);

CREATE TABLE compte_modele_membre (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_modele_compte_org_code UNIQUE (organisation_id, code),
    CONSTRAINT fk_modele_compte_org FOREIGN KEY (organisation_id) REFERENCES organisation(id)
);

ALTER TABLE compte ADD COLUMN modele_compte_id BIGINT;
ALTER TABLE compte ADD CONSTRAINT fk_compte_modele FOREIGN KEY (modele_compte_id) REFERENCES compte_modele_membre(id);
