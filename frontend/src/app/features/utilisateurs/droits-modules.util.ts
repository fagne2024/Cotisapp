import type { TypeProfilDroitDto } from '../../core/services/type-profil.service';
import type { NiveauDroitUi } from './utilisateurs-droits.util';

/** Section catalogue affichée dans le panneau droits (hors organisations plateforme). */
export const SECTIONS_DROITS_ORG = [
  '👥 MEMBRES & UTILISATEURS',
  '💰 OPÉRATIONS FINANCIÈRES',
  '🏦 COMPTES & SOLDES',
  '📈 RAPPORTS & EXPORTS',
  '⚙ PARAMÉTRAGE & ADMINISTRATION',
] as const;

function sectionCourante(lignes: TypeProfilDroitDto[], index: number): string | null {
  let cur: string | null = null;
  for (let i = 0; i <= index; i++) {
    if (lignes[i]?.section) {
      cur = lignes[i].section!;
    }
  }
  return cur;
}

export function sectionPourLigne(lignes: TypeProfilDroitDto[], index: number): string | null {
  return sectionCourante(lignes, index);
}

/** Au moins une action du module (section) est autorisée. */
export function moduleSectionActif(lignes: TypeProfilDroitDto[], sectionLabel: string): boolean {
  let cur: string | null = null;
  for (const l of lignes) {
    if (l.section) {
      cur = l.section;
    }
    if (cur === sectionLabel && l.niveau !== 'NO') {
      return true;
    }
  }
  return false;
}

export function estSectionOrgConfigurable(section: string | null | undefined): boolean {
  return !!section && (SECTIONS_DROITS_ORG as readonly string[]).includes(section);
}

/** Active/désactive toutes les actions d'une section (module menu). */
export function appliquerModuleSection(
  lignes: TypeProfilDroitDto[],
  sectionLabel: string,
  actif: boolean
): TypeProfilDroitDto[] {
  let cur: string | null = null;
  return lignes.map((l) => {
    if (l.section) {
      cur = l.section;
    }
    if (cur !== sectionLabel) {
      return l;
    }
    if (!actif) {
      return { ...l, niveau: 'NO' as NiveauDroitUi };
    }
    if (l.niveau === 'NO') {
      return { ...l, niveau: 'LIM' as NiveauDroitUi };
    }
    return l;
  });
}
