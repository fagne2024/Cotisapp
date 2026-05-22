function escapeCsvCell(value: string | number | null | undefined): string {
  const s = value == null ? '' : String(value);
  if (/[;"\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`;
  return s;
}

/** Télécharge un fichier CSV (séparateur ;, BOM UTF-8 pour Excel). */
export function downloadCsv(filename: string, headers: string[], rows: (string | number)[][]): void {
  const sep = ';';
  const lines = [
    headers.map(escapeCsvCell).join(sep),
    ...rows.map((row) => row.map(escapeCsvCell).join(sep)),
  ];
  const blob = new Blob(['\ufeff' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
