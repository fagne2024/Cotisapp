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
});
