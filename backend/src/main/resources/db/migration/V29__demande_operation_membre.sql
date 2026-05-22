CREATE TABLE demande_operation_membre (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    membre_id BIGINT NOT NULL,
    demandeur_utilisateur_id BIGINT NOT NULL,
    type_demande VARCHAR(40) NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    payload_json LONGTEXT NOT NULL,
    emprunt_id BIGINT NULL,
    montant DECIMAL(19, 2) NOT NULL,
    mode_paiement VARCHAR(30) NULL,
    reference_paiement VARCHAR(120) NULL,
    libelle_resume VARCHAR(500) NULL,
    date_demande DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_traitement DATETIME NULL,
    validateur_utilisateur_id BIGINT NULL,
    motif_refus VARCHAR(500) NULL,
    operation_id BIGINT NULL,
    CONSTRAINT fk_demande_org FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    CONSTRAINT fk_demande_membre FOREIGN KEY (membre_id) REFERENCES membre(id)
);

CREATE INDEX idx_demande_org_statut ON demande_operation_membre (organisation_id, statut);
