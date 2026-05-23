-- Index composite operation(organisation_id, type_operation) pour les filtres fréquents par type
CREATE INDEX idx_operation_org_type ON operation(organisation_id, type_operation);

-- Index composite utilisateur_role(organisation_id, role) pour les lookups admin
CREATE INDEX idx_ur_org_role ON utilisateur_role(organisation_id, role);
