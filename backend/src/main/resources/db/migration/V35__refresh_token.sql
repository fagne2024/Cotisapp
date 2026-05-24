CREATE TABLE refresh_token (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    token       VARCHAR(512) NOT NULL,
    utilisateur_id BIGINT NOT NULL,
    role        VARCHAR(50) NOT NULL,
    organisation_id BIGINT,
    membre_id   BIGINT,
    expires_at  DATETIME NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_token (token(255)),
    CONSTRAINT fk_refresh_token_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur(id)
);
