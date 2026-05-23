import {
  filtreMembreRecapActif,
  filtrePlanadNumeroActif,
  filtrerJourneesParNumero,
  filtrerRecapMembres,
  filtrerRecapOperations,
} from './recap-journee-filtre.util';

describe('recap-journee-filtre.util', () => {
  const membre = {
    membreId: 1,
    codeMembre: 'PLANAD-001',
    membreNom: 'Jean Dupont',
    nbOperations: 2,
    montantCotisations: 1000,
    montantEmprunts: 0,
    montantRemboursements: 0,
    variationNetComptes: 1000,
  };

  const operation = {
    operationId: 10,
    typeOperation: 'COTISATION',
    typeLibelle: 'Cotisation',
    membreId: 1,
    membreNom: 'Jean Dupont',
    codeMembre: 'PLANAD-001',
    montant: 500,
    montantFrais: 0,
    montantTotal: 500,
    dateOperation: '2026-05-16',
    observation: null,
    annulee: false,
    annulation: false,
  };

  it('sans filtre retourne toute la liste', () => {
    expect(filtreMembreRecapActif({ texte: '', codeNumero: '' })).toBe(false);
    expect(filtrerRecapMembres([membre], { texte: '', codeNumero: '' })).toEqual([membre]);
  });

  it('filtre par nom', () => {
    expect(filtrerRecapMembres([membre], { texte: 'dupont', codeNumero: '' })).toEqual([membre]);
    expect(filtrerRecapMembres([membre], { texte: 'martin', codeNumero: '' })).toEqual([]);
  });

  it('filtre par numéro de code', () => {
    expect(filtrerRecapMembres([membre], { texte: '', codeNumero: '001' })).toEqual([membre]);
    expect(filtrerRecapOperations([operation], { texte: '', codeNumero: '1' })).toEqual([operation]);
  });

  const journees = [
    { id: 1, numero: 1, dateReunion: '2026-05-17', libelle: 'PLANAD n°1', statut: 'OUVERT' as const, nbOperations: 12, nbCotisations: 0, nbEmprunts: 0, nbRemboursements: 0 },
    { id: 2, numero: 12, dateReunion: '2026-05-10', libelle: 'PLANAD n°12', statut: 'OUVERT' as const, nbOperations: 5, nbCotisations: 0, nbEmprunts: 0, nbRemboursements: 0 },
  ];

  it('filtre les journées par numéro PLANAD', () => {
    expect(filtrePlanadNumeroActif('')).toBe(false);
    expect(filtrerJourneesParNumero(journees, '')).toEqual(journees);
    expect(filtrerJourneesParNumero(journees, '12')).toEqual([journees[1]]);
    expect(filtrerJourneesParNumero(journees, '99')).toEqual([]);
  });
});
