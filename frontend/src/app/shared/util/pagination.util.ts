export function paginateSlice<T>(items: readonly T[], page: number, pageSize: number): T[] {
  const start = (page - 1) * pageSize;
  return items.slice(start, start + pageSize);
}

export function paginationTotalPages(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / pageSize));
}

export function buildPageNumbers(current: number, totalPages: number): number[] {
  if (totalPages <= 5) {
    return Array.from({ length: totalPages }, (_, i) => i + 1);
  }
  const set = new Set<number>(
    [1, totalPages, current, current - 1, current + 1].filter((n) => n >= 1 && n <= totalPages)
  );
  return [...set].sort((a, b) => a - b);
}

export function buildRangeLabel(page: number, total: number, pageSize: number, unit: string): string {
  if (total === 0) return `Aucun ${unit}`;
  const a = (page - 1) * pageSize + 1;
  const b = Math.min(page * pageSize, total);
  return `Affichage ${a}–${b} sur ${total} ${unit}`;
}

export function clampPage(page: number, totalPages: number): number {
  return Math.min(totalPages, Math.max(1, page));
}
