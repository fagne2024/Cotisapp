-- CotisApp v3 — Schéma initial

CREATE TABLE utilisateur (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    mot_de_passe VARCHAR(255) NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE organisation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    nom VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE membre (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    utilisateur_id BIGINT,
    code_membre VARCHAR(50) NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    telephone VARCHAR(30),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_membre_org FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    CONSTRAINT fk_membre_user FOREIGN KEY (utilisateur_id) REFERENCES utilisateur(id),
    UNIQUE KEY uk_membre_code_org (organisation_id, code_membre)
);

CREATE TABLE utilisateur_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    organisation_id BIGINT,
    membre_id BIGINT,
    CONSTRAINT fk_ur_user FOREIGN KEY (utilisateur_id) REFERENCES utilisateur(id),
    CONSTRAINT fk_ur_org FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    CONSTRAINT fk_ur_membre FOREIGN KEY (membre_id) REFERENCES membre(id)
);

CREATE TABLE compte (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT,
    membre_id BIGINT,
    type_compte VARCHAR(50) NOT NULL,
    proprietaire VARCHAR(20) NOT NULL,
    solde DECIMAL(19,2) NOT NULL DEFAULT 0,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_compte_org FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    CONSTRAINT fk_compte_membre FOREIGN KEY (membre_id) REFERENCES membre(id)
);

CREATE TABLE regle_operation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    type_operation VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    periodicite VARCHAR(20),
    montant_min DECIMAL(19,2),
    montant_max DECIMAL(19,2),
    solidarite_auto BOOLEAN NOT NULL DEFAULT FALSE,
    montant_solidarite_auto DECIMAL(19,2),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_regle_org FOREIGN KEY (organisation_id) REFERENCES organisation(id)
);

CREATE TABLE mouvement_regle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    regle_operation_id BIGINT NOT NULL,
    ordre INT NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    cible_type VARCHAR(50) NOT NULL,
    sens VARCHAR(10) NOT NULL,
    type_montant VARCHAR(20) NOT NULL DEFAULT 'MONTANT_SAISI',
    CONSTRAINT fk_mouv_regle FOREIGN KEY (regle_operation_id) REFERENCES regle_operation(id)
);

CREATE TABLE operation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    membre_id BIGINT,
    type_operation VARCHAR(50) NOT NULL,
    montant DECIMAL(19,2) NOT NULL,
    montant_frais DECIMAL(19,2),
    date_operation DATE NOT NULL,
    observation VARCHAR(500),
    emprunt_id BIGINT,
    echeance_id BIGINT,
    mois_annee VARCHAR(7),
    utilisateur_id BIGINT NOT NULL,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_op_org FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    CONSTRAINT fk_op_membre FOREIGN KEY (membre_id) REFERENCES membre(id)
);

CREATE TABLE mouvement_compte (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operation_id BIGINT NOT NULL,
    compte_id BIGINT NOT NULL,
    sens VARCHAR(10) NOT NULL,
    montant DECIMAL(19,2) NOT NULL,
    CONSTRAINT fk_mc_op FOREIGN KEY (operation_id) REFERENCES operation(id),
    CONSTRAINT fk_mc_compte FOREIGN KEY (compte_id) REFERENCES compte(id)
);

CREATE TABLE emprunt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    membre_id BIGINT NOT NULL,
    type_emprunt VARCHAR(20) NOT NULL,
    montant_total DECIMAL(19,2) NOT NULL,
    montant_rembourse DECIMAL(19,2) NOT NULL DEFAULT 0,
    montant_frais DECIMAL(19,2) DEFAULT 0,
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_COURS',
    date_creation DATE NOT NULL,
    observation VARCHAR(500),
    CONSTRAINT fk_emp_org FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    CONSTRAINT fk_emp_membre FOREIGN KEY (membre_id) REFERENCES membre(id)
);

CREATE TABLE echeance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    emprunt_id BIGINT NOT NULL,
    numero INT NOT NULL,
    montant_echeance DECIMAL(19,2) NOT NULL,
    montant_paye DECIMAL(19,2) NOT NULL DEFAULT 0,
    date_echeance DATE NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'A_PAYER',
    date_paiement DATE,
    CONSTRAINT fk_ech_emp FOREIGN KEY (emprunt_id) REFERENCES emprunt(id)
);

CREATE TABLE suivi_mensuel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    membre_id BIGINT NOT NULL,
    mois_annee VARCHAR(7) NOT NULL,
    montant_du DECIMAL(19,2) NOT NULL,
    montant_paye DECIMAL(19,2) NOT NULL DEFAULT 0,
    statut VARCHAR(20) NOT NULL DEFAULT 'NON_PAYE',
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_paiement DATE,
    CONSTRAINT fk_suivi_org FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    CONSTRAINT fk_suivi_membre FOREIGN KEY (membre_id) REFERENCES membre(id),
    UNIQUE KEY uk_suivi_membre_mois (membre_id, mois_annee)
);

CREATE TABLE journal_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT,
    utilisateur_id BIGINT,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_operation_org ON operation(organisation_id);
CREATE INDEX idx_membre_org ON membre(organisation_id);
CREATE INDEX idx_suivi_mois ON suivi_mensuel(organisation_id, mois_annee);
