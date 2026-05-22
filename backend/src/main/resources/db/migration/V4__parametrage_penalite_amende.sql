INSERT INTO parametrage_compte_organisation (organisation_id, famille, libelle, type_compte, proprietaire, actif)
SELECT o.id, 'PENALITE', 'Compte pénalité', 'PENALITE', 'MEMBRE', FALSE
FROM organisation o
WHERE NOT EXISTS (
    SELECT 1 FROM parametrage_compte_organisation p
    WHERE p.organisation_id = o.id AND p.famille = 'PENALITE'
);

INSERT INTO parametrage_compte_organisation (organisation_id, famille, libelle, type_compte, proprietaire, actif)
SELECT o.id, 'AMENDE', 'Compte amende', 'AMENDE', 'MEMBRE', FALSE
FROM organisation o
WHERE NOT EXISTS (
    SELECT 1 FROM parametrage_compte_organisation p
    WHERE p.organisation_id = o.id AND p.famille = 'AMENDE'
);
