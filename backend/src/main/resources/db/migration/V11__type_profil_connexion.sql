CREATE TABLE type_profil (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL,
    poste_membre VARCHAR(50) NULL,
    canal_connexion VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    ordre INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_type_profil_org FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    CONSTRAINT uk_type_profil_org_code UNIQUE (organisation_id, code)
);

ALTER TABLE utilisateur_role ADD COLUMN type_profil_id BIGINT NULL;
ALTER TABLE utilisateur_role ADD CONSTRAINT fk_ur_type_profil FOREIGN KEY (type_profil_id) REFERENCES type_profil(id);

ALTER TABLE utilisateur ADD COLUMN telephone_normalise VARCHAR(20) NULL;
CREATE INDEX idx_utilisateur_tel_norm ON utilisateur(telephone_normalise);

ALTER TABLE membre ADD COLUMN telephone_normalise VARCHAR(20) NULL;
CREATE INDEX idx_membre_org_tel_norm ON membre(organisation_id, telephone_normalise);

CREATE INDEX idx_journal_audit_user ON journal_audit(utilisateur_id, date_creation);
