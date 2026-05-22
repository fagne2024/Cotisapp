/** Métadonnées d'affichage des postes membres. */
export type PosteKind =
  | 'simple'
  | 'president'
  | 'vice_president'
  | 'sg'
  | 'sga'
  | 'tresorier'
  | 'superviseur';

export type PosteMembreApi =
  | 'SIMPLE'
  | 'PRESIDENT'
  | 'VICE_PRESIDENT'
  | 'SECRETAIRE_GENERAL'
  | 'SECRETAIRE_GENERAL_ADJOINT'
  | 'TRESORIER'
  | 'SUPERVISEUR';

export interface PosteMeta {
  kind: PosteKind;
  label: string;
  badgeClass: string;
  icon: string;
}

const POSTE_BY_KIND: Record<PosteKind, PosteMeta> = {
  simple: { kind: 'simple', label: 'Membre simple', badgeClass: 'pb-simple', icon: '👤' },
  president: { kind: 'president', label: 'Président(e)', badgeClass: 'pb-president', icon: '👑' },
  vice_president: {
    kind: 'vice_president',
    label: 'Vice-président(e)',
    badgeClass: 'pb-vice',
    icon: '🎖',
  },
  sg: { kind: 'sg', label: 'Secrétaire Général', badgeClass: 'pb-sg', icon: '📝' },
  sga: { kind: 'sga', label: 'S.G. Adjoint(e)', badgeClass: 'pb-sga', icon: '📋' },
  tresorier: { kind: 'tresorier', label: 'Trésorier(ère)', badgeClass: 'pb-tresorier', icon: '💼' },
  superviseur: { kind: 'superviseur', label: 'Superviseur', badgeClass: 'pb-superviseur', icon: '🔍' },
};

const API_TO_KIND: Record<PosteMembreApi, PosteKind> = {
  SIMPLE: 'simple',
  PRESIDENT: 'president',
  VICE_PRESIDENT: 'vice_president',
  SECRETAIRE_GENERAL: 'sg',
  SECRETAIRE_GENERAL_ADJOINT: 'sga',
  TRESORIER: 'tresorier',
  SUPERVISEUR: 'superviseur',
};

const KIND_TO_API: Record<PosteKind, PosteMembreApi> = {
  simple: 'SIMPLE',
  president: 'PRESIDENT',
  vice_president: 'VICE_PRESIDENT',
  sg: 'SECRETAIRE_GENERAL',
  sga: 'SECRETAIRE_GENERAL_ADJOINT',
  tresorier: 'TRESORIER',
  superviseur: 'SUPERVISEUR',
};

/** Codes alignés sur la maquette (rétrocompat démo). */
const BY_CODE: Record<string, PosteMeta> = {
  'GDR-003': POSTE_BY_KIND.president,
  'GDR-007': POSTE_BY_KIND.sg,
  'GDR-009': POSTE_BY_KIND.sga,
  'GDR-004': POSTE_BY_KIND.tresorier,
  'GDR-012': POSTE_BY_KIND.superviseur,
};

export function postePourKind(kind: PosteKind): PosteMeta {
  return POSTE_BY_KIND[kind] ?? POSTE_BY_KIND.simple;
}

export function postePourMembre(code: string, posteApi?: PosteMembreApi | string | null): PosteMeta {
  if (posteApi && posteApi in API_TO_KIND) {
    return postePourKind(API_TO_KIND[posteApi as PosteMembreApi]);
  }
  return BY_CODE[code] ?? POSTE_BY_KIND.simple;
}

/** @deprecated Utiliser {@link postePourMembre}. */
export function postePourCodeMembre(code: string): PosteMeta {
  return postePourMembre(code, null);
}

export function posteKindVersApi(kind: PosteKind): PosteMembreApi {
  return KIND_TO_API[kind];
}

export function estPosteBureau(kind: PosteKind): boolean {
  return kind !== 'simple';
}

/** Soldes factices stables (en attendant API comptes membre). */
export function soldeEpargnePlaceholder(membreId: number): number {
  return 10000 + (membreId * 7919) % 52000;
}

export function soldeSolidaritePlaceholder(membreId: number): number {
  return 4000 + (membreId * 4999) % 4200;
}
