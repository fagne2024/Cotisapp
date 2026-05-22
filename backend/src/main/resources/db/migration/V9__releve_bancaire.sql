CREATE TABLE releve_bancaire (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    operation_id BIGINT NOT NULL,
    nom_fichier VARCHAR(255) NOT NULL,
    chemin_stockage VARCHAR(500) NOT NULL,
    type_mime VARCHAR(100),
    taille_octets BIGINT NOT NULL,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_releve_org FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    CONSTRAINT fk_releve_op FOREIGN KEY (operation_id) REFERENCES operation(id),
    UNIQUE KEY uk_releve_operation (operation_id)
);

CREATE INDEX idx_releve_org ON releve_bancaire(organisation_id);
