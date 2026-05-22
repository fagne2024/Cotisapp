ALTER TABLE compte ADD COLUMN libelle VARCHAR(255);

CREATE TABLE parametrage_compte_organisation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    famille VARCHAR(30) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    type_compte VARCHAR(50) NOT NULL,
    proprietaire VARCHAR(20) NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_param_compte_org UNIQUE (organisation_id, famille),
    CONSTRAINT fk_param_compte_org FOREIGN KEY (organisation_id) REFERENCES organisation(id)
);
