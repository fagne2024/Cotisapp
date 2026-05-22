export type ModePaiement = 'ESPECES' | 'WAVE' | 'ORANGE_MONEY';

export interface ModePaiementOption {
  value: ModePaiement;
  label: string;
  logoUrl?: string;
}

export const MODES_PAIEMENT: ModePaiementOption[] = [
  { value: 'ESPECES', label: 'Espèces' },
  { value: 'WAVE', label: 'Wave', logoUrl: 'assets/paiement/wave.png' },
  { value: 'ORANGE_MONEY', label: 'Orange Money', logoUrl: 'assets/paiement/orange-money.png' },
];

export function modePaiementMobile(mode: ModePaiement | string | null | undefined): boolean {
  return mode === 'WAVE' || mode === 'ORANGE_MONEY';
}

export function libelleModePaiement(mode: ModePaiement | string | null | undefined): string {
  const found = MODES_PAIEMENT.find((m) => m.value === mode);
  return found?.label ?? 'Espèces';
}
