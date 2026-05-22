import {
  filtrerMembresParNumeroCode,
  numeroCorrespondAuCode,
  suffixeCodeNumerique,
} from './membre-code-lookup.util';

describe('membre-code-lookup.util', () => {
  it('extrait le suffixe numerique', () => {
    expect(suffixeCodeNumerique('PLANAD-001')).toBe('001');
  });

  it('correspond au numero ou au suffixe', () => {
    expect(numeroCorrespondAuCode('PLANAD-001', '001')).toBe(true);
    expect(numeroCorrespondAuCode('PLANAD-001', '1')).toBe(true);
    expect(numeroCorrespondAuCode('PLANAD-001', '002')).toBe(false);
  });

  it('filtre les membres par numero', () => {
    const list = [
      { id: 1, codeMembre: 'PLANAD-001', nomComplet: 'A' },
      { id: 2, codeMembre: 'PLANAD-002', nomComplet: 'B' },
    ] as Parameters<typeof filtrerMembresParNumeroCode>[0];
    expect(filtrerMembresParNumeroCode(list, '001')).toHaveLength(1);
    expect(filtrerMembresParNumeroCode(list, '001')[0].id).toBe(1);
  });
});
