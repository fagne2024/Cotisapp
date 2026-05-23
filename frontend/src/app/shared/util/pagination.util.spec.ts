import {
  buildPageNumbers,
  buildRangeLabel,
  paginateSlice,
  paginationTotalPages,
} from './pagination.util';

describe('pagination.util', () => {
  it('paginateSlice retourne la tranche demandée', () => {
    expect(paginateSlice([1, 2, 3, 4, 5], 2, 2)).toEqual([3, 4]);
  });

  it('paginationTotalPages calcule le nombre de pages', () => {
    expect(paginationTotalPages(0, 10)).toBe(1);
    expect(paginationTotalPages(25, 10)).toBe(3);
  });

  it('buildRangeLabel formate le libellé', () => {
    expect(buildRangeLabel(2, 30, 10, 'élément(s)')).toBe('Affichage 11–20 sur 30 élément(s)');
  });

  it('buildPageNumbers limite les numéros affichés', () => {
    expect(buildPageNumbers(5, 10)).toEqual([1, 4, 5, 6, 10]);
  });

  it('paginateSlice retourne [] si pageSize vaut 0', () => {
    expect(paginateSlice([1, 2, 3], 1, 0)).toEqual([]);
  });

  it('paginateSlice traite page=0 comme page=1', () => {
    expect(paginateSlice([1, 2, 3, 4, 5], 0, 2)).toEqual([1, 2]);
  });

  it('paginationTotalPages retourne 1 si pageSize vaut 0', () => {
    expect(paginationTotalPages(50, 0)).toBe(1);
  });

  it('buildRangeLabel avec page=0 ne produit pas d\'indices négatifs', () => {
    const label = buildRangeLabel(0, 30, 10, 'élément(s)');
    expect(label).toBe('Affichage 1–10 sur 30 élément(s)');
  });
});
