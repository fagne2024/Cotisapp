CREATE TABLE notification_etat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organisation_id BIGINT NOT NULL,
    utilisateur_id BIGINT NOT NULL,
    cle_notification VARCHAR(120) NOT NULL,
    lu BOOLEAN NOT NULL DEFAULT FALSE,
    masque BOOLEAN NOT NULL DEFAULT FALSE,
    date_modification TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_etat_org FOREIGN KEY (organisation_id) REFERENCES organisation(id),
    CONSTRAINT fk_notif_etat_user FOREIGN KEY (utilisateur_id) REFERENCES utilisateur(id),
    UNIQUE KEY uk_notif_etat (utilisateur_id, organisation_id, cle_notification)
);

CREATE INDEX idx_notif_etat_org_user ON notification_etat (organisation_id, utilisateur_id);
