export function formatFcfa(amount: number | string | null | undefined): string {
  if (amount == null || amount === '') return '0 F';
  const n = typeof amount === 'string' ? parseFloat(amount) : amount;
  const formatted = new Intl.NumberFormat('fr-FR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(n);
  return `${formatted} F`;
}
