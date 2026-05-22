CREATE TABLE action_droit (
    code VARCHAR(80) PRIMARY KEY,
    section VARCHAR(120) NULL,
    libelle VARCHAR(200) NOT NULL,
    ordre INT NOT NULL DEFAULT 0
);

CREATE TABLE type_profil_droit (
    type_profil_id BIGINT NOT NULL,
    action_code VARCHAR(80) NOT NULL,
    niveau VARCHAR(10) NOT NULL,
    PRIMARY KEY (type_profil_id, action_code),
    CONSTRAINT fk_tpd_type_profil FOREIGN KEY (type_profil_id) REFERENCES type_profil(id) ON DELETE CASCADE,
    CONSTRAINT fk_tpd_action FOREIGN KEY (action_code) REFERENCES action_droit(code)
);

CREATE INDEX idx_type_profil_droit_profil ON type_profil_droit(type_profil_id);
