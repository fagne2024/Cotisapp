CREATE TABLE journee_reunion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    numero INT NOT NULL,
    date_reunion DATE NOT NULL,
    libelle VARCHAR(80) NOT NULL,
    date_creation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_journee_org_date UNIQUE (organisation_id, date_reunion),
    CONSTRAINT uk_journee_org_numero UNIQUE (organisation_id, numero),
    CONSTRAINT fk_journee_organisation FOREIGN KEY (organisation_id) REFERENCES organisation (id)
);

CREATE INDEX idx_journee_org ON journee_reunion (organisation_id, date_reunion DESC);
