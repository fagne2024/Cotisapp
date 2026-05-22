ALTER TABLE journal_audit

    ADD COLUMN type_evenement VARCHAR(40) NOT NULL DEFAULT 'ACTION' AFTER action,

    ADD COLUMN module_code VARCHAR(80) NULL AFTER type_evenement,

    ADD COLUMN module_libelle VARCHAR(200) NULL AFTER module_code,

    ADD COLUMN route_path VARCHAR(500) NULL AFTER module_libelle,

    ADD COLUMN utilisateur_email VARCHAR(255) NULL AFTER utilisateur_id,

    ADD COLUMN utilisateur_nom VARCHAR(255) NULL AFTER utilisateur_email,

    ADD COLUMN role VARCHAR(30) NULL AFTER utilisateur_nom,

    ADD COLUMN membre_id BIGINT NULL AFTER role,

    ADD COLUMN ip_address VARCHAR(64) NULL AFTER details,

    ADD COLUMN user_agent VARCHAR(500) NULL AFTER ip_address,

    ADD COLUMN succes TINYINT(1) NOT NULL DEFAULT 1 AFTER user_agent;



UPDATE journal_audit SET type_evenement = 'CONNEXION'

WHERE action IN ('CONNEXION_REUSSIE', 'CONNEXION_2FA_REUSSIE');



CREATE INDEX idx_journal_audit_org_date ON journal_audit(organisation_id, date_creation DESC);

CREATE INDEX idx_journal_audit_org_type ON journal_audit(organisation_id, type_evenement, date_creation DESC);

