import {
  buildCarteEmprunts,
  buildComptesCartes,
  sommeEncoursEmpruntsAvecFrais,
  sommesOperationsParCompte,
} from './membre-fiche.util';
import { OperationMembreDto } from '../../core/services/membre.service';
import { EmpruntDto } from '../../core/services/emprunt.service';

describe('membre-fiche.util — cumul opérations', () => {
  it('additionne les cotisations hebdomadaires', () => {
    const ops: OperationMembreDto[] = [
      {
        id: 1,
        typeOperation: 'COTISATION',
        montant: 10000,
        dateOperation: '2026-05-01',
      },
      {
        id: 2,
        typeOperation: 'COTISATION',
        montant: 5000,
        dateOperation: '2026-05-08',
      },
    ];
    const cumuls = sommesOperationsParCompte(ops, []);
    expect(cumuls.get('EPARGNE_HEBDO')?.total).toBe(15000);
    expect(cumuls.get('EPARGNE_HEBDO')?.count).toBe(2);
  });

  it('carte épargne hebdo affiche le cumul', () => {
    const cartes = buildComptesCartes(
      [{ id: 1, typeCompte: 'EPARGNE_HEBDO', libelle: 'Épargne hebdo', solde: 999 }],
      [
        {
          id: 1,
          typeOperation: 'COTISATION',
          montant: 10000,
          dateOperation: '2026-05-01',
        },
      ],
      []
    );
    expect(cartes[0].valeur).toBe(10000);
    expect(cartes[0].valeur).not.toBe(999);
  });

  it('cumule la part solidarité des cotisations hebdo et mois', () => {
    const ops: OperationMembreDto[] = [
      {
        id: 1,
        typeOperation: 'COTISATION',
        montant: 10000,
        montantSolidarite: 200,
        dateOperation: '2026-05-01',
      },
      {
        id: 2,
        typeOperation: 'COTISATION_MOIS',
        montant: 5000,
        montantSolidarite: 200,
        dateOperation: '2026-05-10',
      },
    ];
    const cumuls = sommesOperationsParCompte(ops, []);
    expect(cumuls.get('EPARGNE_HEBDO')?.total).toBe(10000);
    expect(cumuls.get('EPARGNE_MOIS')?.total).toBe(5000);
    expect(cumuls.get('SOLIDARITE')?.total).toBe(400);
    expect(cumuls.get('SOLIDARITE')?.count).toBe(2);
  });

  it('carte emprunts affiche encours capital + frais restants', () => {
    const emprunts: EmpruntDto[] = [
      {
        id: 1,
        membreId: 1,
        membreNom: 'Test',
        codeMembre: 'M001',
        typeEmprunt: 'CAISSE',
        montantTotal: 10200,
        montantRembourse: 2000,
        montantRestant: 8200,
        montantFrais: 200,
        statut: 'EN_COURS',
        echeances: [],
      },
    ];
    const agg = sommeEncoursEmpruntsAvecFrais(emprunts);
    expect(agg.total).toBe(8200);
    const carte = buildCarteEmprunts(emprunts);
    expect(carte.valeur).toBe(8200);
    expect(carte.sousTitre).toContain('1 en cours');
  });
});
