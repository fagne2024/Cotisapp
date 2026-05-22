ALTER TABLE operation
    ADD COLUMN annulee BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN operation_origine_id BIGINT NULL;

ALTER TABLE operation
    ADD CONSTRAINT fk_op_origine FOREIGN KEY (operation_origine_id) REFERENCES operation (id);

CREATE INDEX idx_operation_origine ON operation (operation_origine_id);
CREATE INDEX idx_operation_annulee ON operation (organisation_id, annulee);
