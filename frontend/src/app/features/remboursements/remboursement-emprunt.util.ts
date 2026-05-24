import { EmpruntDto, EcheanceDto, TypeEmprunt } from '../../core/services/emprunt.service';
import { RegleOperationDto } from '../../core/services/regle-operation.service';
import { calculerPenalite } from '../parametrage/regle-emprunt-calcul.util';
import {
  JOURS_ALERTE_ECHEANCE_PROCHE as JOURS_ALERTE_DEFAUT,
  libellePenaliteEmprunt,
} from '../../core/util/regle-emprunt.util';
import { formatFcfa } from '../../core/utils/currency.util';

export type RembAlertLevel = 'info' | 'warn' | 'error' | 'ok';

export interface RembAlerte {
  level: RembAlertLevel;
  icon: string;
  message: string;
}

export function echeanceRestant(ech: EcheanceDto): number {
  return Math.max(0, Number(ech.montantEcheance) - Number(ech.montantPaye));
}

/** Répartition nominal / frais d'une échéance caisse (montant échéance = nominal + frais). */
export interface RepartitionEcheanceCaisse {
  capitalNominal: number;
  frais: number;
  capitalRestant: number;
  fraisRestant: number;
  totalRestant: number;
}

export function repartitionEcheanceCaisse(emp: EmpruntDto, ech: EcheanceDto): RepartitionEcheanceCaisse {
  const totalEmprunt = Number(emp.montantTotal) || 0;
  const fraisTotal = Math.max(0, Number(emp.montantFrais) || 0);
  const capitalTotal = Math.max(0, totalEmprunt - fraisTotal);
  const montantEch = Number(ech.montantEcheance) || 0;
  const totalRestant = echeanceRestant(ech);

  if (totalEmprunt <= 0 || montantEch <= 0) {
    return { capitalNominal: 0, frais: 0, capitalRestant: 0, fraisRestant: 0, totalRestant };
  }

  const capitalNominal = Math.round((montantEch * capitalTotal) / totalEmprunt);
  const frais = Math.max(0, Math.round(montantEch - capitalNominal));

  if (totalRestant >= montantEch - 0.01) {
    return { capitalNominal, frais, capitalRestant: capitalNominal, fraisRestant: frais, totalRestant };
  }

  const ratio = totalRestant / montantEch;
  const capitalRestant = Math.round(capitalNominal * ratio);
  const fraisRestant = Math.max(0, Math.round(totalRestant - capitalRestant));

  return { capitalNominal, frais, capitalRestant, fraisRestant, totalRestant };
}

export function empruntEnRetard(emp: EmpruntDto, refDate = new Date()): boolean {
  const today = new Date(refDate);
  today.setHours(0, 0, 0, 0);
  return (emp.echeances ?? []).some((ech) => {
    if (ech.statut === 'PAYE') return false;
    const d = new Date(ech.dateEcheance + 'T12:00:00');
    return d < today;
  });
}

export function echeanceEnRetard(ech: EcheanceDto, refDate = new Date()): boolean {
  if (ech.statut === 'PAYE') return false;
  const today = new Date(refDate);
  today.setHours(0, 0, 0, 0);
  const d = new Date(ech.dateEcheance + 'T12:00:00');
  return d < today;
}

/** Nombre de jours avant l'échéance pour déclencher une alerte « proche » (défaut ; voir règle emprunt). */
export const JOURS_ALERTE_ECHEANCE_PROCHE = JOURS_ALERTE_DEFAUT;

export function joursAvantEcheance(dateEcheance: string, refDate = new Date()): number {
  const today = new Date(refDate);
  today.setHours(0, 0, 0, 0);
  const d = new Date(dateEcheance + 'T12:00:00');
  d.setHours(0, 0, 0, 0);
  return Math.floor((d.getTime() - today.getTime()) / 86_400_000);
}

/** Échéance à payer dans les N prochains jours (aujourd'hui inclus), sans être en retard. */
export function echeanceProche(
  ech: EcheanceDto,
  seuilJours = JOURS_ALERTE_ECHEANCE_PROCHE,
  refDate = new Date()
): boolean {
  if (ech.statut === 'PAYE') return false;
  if (echeanceEnRetard(ech, refDate)) return false;
  const jours = joursAvantEcheance(ech.dateEcheance, refDate);
  return jours >= 0 && jours <= seuilJours;
}

export function empruntEcheanceProche(
  emp: EmpruntDto,
  seuilJours = JOURS_ALERTE_ECHEANCE_PROCHE,
  refDate = new Date()
): boolean {
  return (emp.echeances ?? []).some((e) => echeanceProche(e, seuilJours, refDate));
}

/** Première échéance ouverte dans la fenêtre « proche » (hors retard). */
export function premiereEcheanceProche(
  emp: EmpruntDto,
  seuilJours = JOURS_ALERTE_ECHEANCE_PROCHE,
  refDate = new Date()
): EcheanceDto | null {
  return (
    (emp.echeances ?? [])
      .filter((e) => echeanceProche(e, seuilJours, refDate))
      .sort((a, b) => a.numero - b.numero)[0] ?? null
  );
}

/** Échéance à solder en priorité : la plus ancienne en retard, sinon la prochaine ouverte. */
export function echeancePrioritairePourRemboursement(emp: EmpruntDto): EcheanceDto | null {
  const ouvertes = echeancesOuvertes(emp);
  const enRetard = ouvertes.filter((e) => echeanceEnRetard(e)).sort((a, b) => a.numero - b.numero);
  if (enRetard.length) return enRetard[0];
  return prochaineEcheanceOuverte(emp);
}

/** Prochaine échéance non soldée (numéro le plus bas). */
export function prochaineEcheanceOuverte(emp: EmpruntDto): EcheanceDto | null {
  const ouvertes = (emp.echeances ?? [])
    .filter((e) => e.statut !== 'PAYE')
    .sort((a, b) => a.numero - b.numero);
  return ouvertes[0] ?? null;
}

export function echeancesOuvertes(emp: EmpruntDto): EcheanceDto[] {
  return (emp.echeances ?? [])
    .filter((e) => e.statut !== 'PAYE')
    .sort((a, b) => a.numero - b.numero);
}

/** Reste de l'avance Caisse à rembourser à la Caisse (emprunt Solidarité). */
export function avanceCaisseRestantEmprunt(emp: EmpruntDto): number {
  if (emp.typeEmprunt !== 'SOLIDARITE') return 0;
  const avance = Number(emp.montantAvanceCaisse ?? 0);
  const remb = Number(emp.montantRembourseAvanceCaisse ?? 0);
  return Math.max(0, avance - remb);
}

/** Répartition d'un remboursement Solidarité : d'abord la dette Caisse, puis le fonds Solidarité. */
export function repartirRemboursementSolidarite(
  emp: EmpruntDto,
  montantPaiement: number
): { partCaisse: number; partSolidarite: number } {
  const montant = Math.max(0, montantPaiement);
  const restantAvance = avanceCaisseRestantEmprunt(emp);
  const partCaisse = Math.min(montant, restantAvance);
  return { partCaisse, partSolidarite: montant - partCaisse };
}

export function progressPctEmprunt(emp: EmpruntDto): number {
  if (!emp.montantTotal) return 0;
  return Math.min(100, Math.round((emp.montantRembourse / emp.montantTotal) * 100));
}

/** Référence affichée type GDR-EMP-001 (basée sur l'id emprunt). */
export function referenceEmprunt(emp: EmpruntDto): string {
  return `GDR-EMP-${String(emp.id).padStart(3, '0')}`;
}

/** Libellé du sélecteur d'emprunt (maquette). */
export function empruntLabelSelect(emp: EmpruntDto): string {
  const retard = empruntEnRetard(emp) ? ' ⚠ Retard' : '';
  return `${referenceEmprunt(emp)} · ${emp.membreNom} · ${typeEmpruntLabel(emp.typeEmprunt)} · ${formatFcfa(emp.montantRestant)} restants${retard}`;
}

export function premiereEcheanceEnRetard(emp: EmpruntDto): EcheanceDto | null {
  return (
    (emp.echeances ?? [])
      .filter((e) => echeanceEnRetard(e))
      .sort((a, b) => a.numero - b.numero)[0] ?? null
  );
}

export function badgeRetardEmprunt(emp: EmpruntDto): string | null {
  const ech = premiereEcheanceEnRetard(emp);
  return ech ? `⚠ Retard éch. ${ech.numero}` : null;
}

export type StatutEcheanceUi = 'paye' | 'retard' | 'proche' | 'avenir' | 'partiel' | 'apayer';

export function statutEcheanceUi(
  ech: EcheanceDto,
  refDate: Date = new Date(),
  seuilJours = JOURS_ALERTE_ECHEANCE_PROCHE,
): StatutEcheanceUi {
  if (ech.statut === 'PAYE') return 'paye';
  if (echeanceEnRetard(ech, refDate)) return 'retard';
  if (ech.statut === 'PARTIEL') return 'partiel';
  if (echeanceProche(ech, seuilJours, refDate)) return 'proche';
  const today = new Date(refDate);
  today.setHours(0, 0, 0, 0);
  const d = new Date(ech.dateEcheance + 'T12:00:00');
  if (d > today) return 'avenir';
  return 'apayer';
}

export function statutEcheanceLabel(ech: EcheanceDto, refDate = new Date()): string {
  const ui = statutEcheanceUi(ech, refDate);
  const jours = joursAvantEcheance(ech.dateEcheance, refDate);
  const labels: Record<StatutEcheanceUi, string> = {
    paye: '✓ Payée',
    retard: '⚠ En retard',
    proche: jours === 0 ? "⏰ Aujourd'hui" : `⏰ Dans ${jours} j`,
    avenir: 'À venir',
    partiel: 'Partiel',
    apayer: 'À payer',
  };
  return labels[ui];
}

/** Libellé court pour le champ « échéance sélectionnée » du formulaire. */
export function echeanceLabelForm(ech: EcheanceDto): string {
  return `Échéance ${ech.numero} — ${formatFcfa(echeanceRestant(ech))}`;
}

export function typeEmpruntLabel(t: TypeEmprunt): string {
  const m: Record<TypeEmprunt, string> = {
    ETALE: 'Étalé',
    SOLIDARITE: 'Solidarité',
    CAISSE: 'Caisse',
  };
  return m[t] ?? t;
}

export function formatDateFr(iso: string): string {
  if (!iso) return '—';
  const d = new Date(iso + 'T12:00:00');
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(d);
}

export function echeanceLabelComplet(e: EcheanceDto, emp?: EmpruntDto | null): string {
  const rest = echeanceRestant(e);
  const retard = echeanceEnRetard(e) ? ' · ⚠ retard' : '';
  const stat =
    e.statut === 'PARTIEL' ? ' (partiel)' : e.statut === 'PAYE' ? ' (payé)' : '';
  let montants: string;
  if (emp?.typeEmprunt === 'CAISSE' && (emp.montantFrais ?? 0) > 0) {
    const p = repartitionEcheanceCaisse(emp, e);
    montants = `nom. ${formatFcfa(p.capitalRestant)} + frais ${formatFcfa(p.fraisRestant)} / ${formatFcfa(e.montantEcheance)}`;
  } else {
    montants = `${formatFcfa(rest)} / ${formatFcfa(e.montantEcheance)}`;
  }
  return `Éch. ${e.numero} — ${montants} — ${formatDateFr(e.dateEcheance)}${stat}${retard}`;
}

export interface PenaliteRetardCalc {
  applicable: boolean;
  montant: number;
  moisRetard: number;
  base: number;
}

export interface BuildAlertesParams {
  emp: EmpruntDto | null;
  regle: RegleOperationDto | null;
  avecEcheance: boolean;
  avecFrais: boolean;
  montant: number;
  montantCapital: number;
  montantFrais: number;
  montantPenalite: number;
  appliquerPenalite: boolean;
  echeanceId: number | null;
  datePaiement?: Date | string | null;
}

/** Mois de retard (au moins 1 dès le lendemain de l'échéance). */
export function moisEnRetard(dateEcheanceIso: string, dateRef: Date = new Date()): number {
  const ech = new Date(dateEcheanceIso + 'T12:00:00');
  const ref = new Date(dateRef);
  ech.setHours(0, 0, 0, 0);
  ref.setHours(0, 0, 0, 0);
  if (ref <= ech) return 0;
  const jours = Math.floor((ref.getTime() - ech.getTime()) / 86400000);
  return Math.max(1, Math.ceil(jours / 30));
}

export function echeancePourPenalite(
  emp: EmpruntDto,
  echeanceId: number | null,
  datePaiement: Date = new Date()
): EcheanceDto | null {
  if (echeanceId != null) {
    return (emp.echeances ?? []).find((e) => e.id === echeanceId) ?? null;
  }
  const ouvertes = (emp.echeances ?? [])
    .filter((e) => e.statut !== 'PAYE')
    .filter((e) => {
      const d = new Date(e.dateEcheance + 'T12:00:00');
      const ref = new Date(datePaiement);
      d.setHours(0, 0, 0, 0);
      ref.setHours(0, 0, 0, 0);
      return ref > d;
    })
    .sort((a, b) => a.numero - b.numero);
  return ouvertes[0] ?? null;
}

export function calculerPenaliteRetard(
  emp: EmpruntDto | null,
  regle: RegleOperationDto | null | undefined,
  echeanceId: number | null,
  datePaiement: Date = new Date()
): PenaliteRetardCalc {
  if (!emp || !regle) {
    return { applicable: false, montant: 0, moisRetard: 0, base: 0 };
  }
  const ech = echeancePourPenalite(emp, echeanceId, datePaiement);
  if (!ech || !echeanceEnRetard(ech, datePaiement)) {
    return { applicable: false, montant: 0, moisRetard: 0, base: 0 };
  }
  const base = echeanceRestant(ech);
  const mois = moisEnRetard(ech.dateEcheance, datePaiement);
  const unitaire = calculerPenalite(base, regle.typePenalite, regle.montantPenalite, regle.pourcentagePenalite);
  return {
    applicable: true,
    montant: unitaire * mois,
    moisRetard: mois,
    base,
  };
}

export function buildRemboursementAlertes(p: BuildAlertesParams): RembAlerte[] {
  const alertes: RembAlerte[] = [];
  if (!p.emp) {
    alertes.push({ level: 'info', icon: 'ℹ️', message: 'Sélectionnez un emprunt en cours pour saisir le remboursement.' });
    return alertes;
  }

  const emp = p.emp;
  const ech =
    p.echeanceId != null
      ? (emp.echeances ?? []).find((e) => e.id === p.echeanceId) ?? null
      : prochaineEcheanceOuverte(emp);

  if (p.regle && !p.regle.actif) {
    alertes.push({
      level: 'warn',
      icon: '⚙',
      message: 'La règle d\'emprunt associée est inactive dans le paramétrage.',
    });
  }

  alertes.push({
    level: 'info',
    icon: '📋',
    message: `Emprunt ${typeEmpruntLabel(emp.typeEmprunt)} · Capital ${formatFcfa(emp.montantTotal - (emp.montantFrais ?? 0))} · Total dû ${formatFcfa(emp.montantTotal)} · Reste ${formatFcfa(emp.montantRestant)}`,
  });

  const dateRef = p.datePaiement instanceof Date ? p.datePaiement : p.datePaiement ? new Date(p.datePaiement) : new Date();
  const penCalc = p.appliquerPenalite
    ? calculerPenaliteRetard(emp, p.regle, p.echeanceId, dateRef)
    : { applicable: false, montant: 0, moisRetard: 0, base: 0 };

  if (empruntEnRetard(emp)) {
    const pen = p.regle ? libellePenaliteEmprunt(p.regle) : 'Pénalité de retard paramétrée';
    if (penCalc.applicable && penCalc.montant > 0) {
      alertes.push({
        level: 'warn',
        icon: '⚠',
        message: `Retard ${penCalc.moisRetard} mois — pénalité calculée : ${formatFcfa(penCalc.montant)} (${pen}, base ${formatFcfa(penCalc.base)}).`,
      });
    } else {
      alertes.push({
        level: 'warn',
        icon: '⚠',
        message: `Échéance(s) en retard — ${pen}.`,
      });
    }
  }

  if (p.appliquerPenalite && penCalc.applicable && p.montantPenalite > 0) {
    alertes.push({
      level: 'info',
      icon: '💰',
      message: `Pénalité incluse : ${formatFcfa(p.montantPenalite)} (débitée du compte membre, crédit organisation).`,
    });
  } else if (p.appliquerPenalite && penCalc.applicable && penCalc.montant > 0 && p.montantPenalite === 0) {
    alertes.push({
      level: 'info',
      icon: 'ℹ️',
      message: 'Pénalité non appliquée (montant à 0). Activez le montant suggéré si besoin.',
    });
  }

  if (p.avecEcheance && echeancesOuvertes(emp).length > 0) {
    if (!p.echeanceId || !ech) {
      alertes.push({
        level: 'error',
        icon: '❌',
        message: 'Choisissez l\'échéance à solder (montant prérempli depuis l\'échéancier de l\'emprunt).',
      });
    } else if (ech) {
      const restEch = echeanceRestant(ech);
      const attenduMsg =
        p.avecFrais && emp.typeEmprunt === 'CAISSE'
          ? (() => {
              const parts = repartitionEcheanceCaisse(emp, ech);
              return `nominal ${formatFcfa(parts.capitalRestant)} + frais ${formatFcfa(parts.fraisRestant)} = ${formatFcfa(parts.totalRestant)}`;
            })()
          : formatFcfa(restEch);
      if (echeanceEnRetard(ech)) {
        alertes.push({
          level: 'warn',
          icon: '📅',
          message: `Échéance n°${ech.numero} due le ${formatDateFr(ech.dateEcheance)} — montant attendu : ${attenduMsg}.`,
        });
      } else {
        alertes.push({
          level: 'ok',
          icon: '✓',
          message: `Échéance n°${ech.numero} — dû : ${attenduMsg} (déjà payé ${formatFcfa(ech.montantPaye)}).`,
        });
      }
    }
  } else if (!p.avecEcheance) {
    alertes.push({
      level: 'info',
      icon: '🤝',
      message: `Remboursement libre sur le solde restant (${formatFcfa(emp.montantRestant)}).`,
    });
  }

  const montantRemb = p.avecFrais ? p.montantCapital + p.montantFrais : p.montant;
  const totalDebit = montantRemb + (p.appliquerPenalite ? p.montantPenalite : 0);
  if (montantRemb <= 0) {
    alertes.push({ level: 'error', icon: '❌', message: 'Le montant du remboursement doit être supérieur à 0.' });
  } else if (montantRemb > emp.montantRestant + 0.01) {
    alertes.push({
      level: 'error',
      icon: '❌',
      message: `Le montant dépasse le reste à rembourser (${formatFcfa(emp.montantRestant)}).`,
    });
  } else if (totalDebit > 0) {
    alertes.push({
      level: 'info',
      icon: '🧾',
      message: `Total débité membre : ${formatFcfa(totalDebit)} (remboursement ${formatFcfa(montantRemb)}${p.montantPenalite > 0 ? ' + pénalité ' + formatFcfa(p.montantPenalite) : ''}).`,
    });
  }

  if (ech && p.avecEcheance) {
    const restEch = echeanceRestant(ech);
    if (p.avecFrais && emp.typeEmprunt === 'CAISSE') {
      const parts = repartitionEcheanceCaisse(emp, ech);
      if (p.montantCapital > parts.capitalRestant + 0.01) {
        alertes.push({
          level: 'error',
          icon: '❌',
          message: `Le capital dépasse le nominal restant de l'échéance (${formatFcfa(parts.capitalRestant)}).`,
        });
      }
      if (p.montantFrais > parts.fraisRestant + 0.01) {
        alertes.push({
          level: 'error',
          icon: '❌',
          message: `Les frais dépassent la part restante sur l'échéance (${formatFcfa(parts.fraisRestant)}).`,
        });
      }
    }
    if (montantRemb > restEch + 0.01) {
      alertes.push({
        level: 'error',
        icon: '❌',
        message: `Le montant dépasse le reste de l'échéance (${formatFcfa(restEch)}).`,
      });
    } else if (Math.abs(montantRemb - restEch) < 0.01) {
      alertes.push({
        level: 'ok',
        icon: '✅',
        message: p.avecFrais && emp.typeEmprunt === 'CAISSE'
          ? 'Capital et frais conformes au solde de l\'échéance.'
          : 'Montant conforme au solde de l\'échéance sélectionnée.',
      });
    } else {
      alertes.push({
        level: 'info',
        icon: 'ℹ️',
        message: `Paiement partiel : il restera ${formatFcfa(restEch - montantRemb)} sur cette échéance.`,
      });
    }
  }

  if (p.avecFrais && emp.montantFrais != null && emp.montantFrais > 0) {
    alertes.push({
      level: 'info',
      icon: '💼',
      message: `Frais enregistrés à l'octroi : ${formatFcfa(emp.montantFrais)} (débités sur le compte membre). Au remboursement, les frais créditent le membre et la caisse ; à l'emprunt soldé, le total des frais est transféré vers le compte intérêts.`,
    });
  }

  return alertes;
}

export function remboursementBloque(alertes: RembAlerte[]): boolean {
  return alertes.some((a) => a.level === 'error');
}

export interface MontantRemboursementSaisie {
  montant: number;
  montantCapital: number;
  montantFrais: number;
  appliquerPenalite: boolean;
  echeanceId: number | null;
}

/** Montant total qui sera enregistré pour un emprunt (capital + frais + pénalité éventuelle). */
export function montantRemboursementEffectif(
  emp: EmpruntDto,
  cfg: { avecEcheance: boolean; avecFrais: boolean },
  saisie: MontantRemboursementSaisie,
  regle: RegleOperationDto,
  datePaiement: Date | string
): number {
  let ech: EcheanceDto | null = null;
  if (cfg.avecEcheance) {
    ech =
      (saisie.echeanceId != null
        ? (emp.echeances ?? []).find((e) => e.id === saisie.echeanceId)
        : null) ?? echeancePrioritairePourRemboursement(emp);
  }

  let base = 0;
  if (cfg.avecEcheance && ech) {
    if (cfg.avecFrais) {
      const parts = repartitionEcheanceCaisse(emp, ech);
      const capC = Math.min(
        Number(saisie.montantCapital) || parts.capitalRestant,
        parts.capitalRestant
      );
      const capF = Math.min(Number(saisie.montantFrais) || 0, parts.fraisRestant);
      base = (capC > 0 ? capC : parts.capitalRestant) + capF;
    } else {
      const rest = echeanceRestant(ech);
      const m = Math.min(Number(saisie.montant) || rest, rest);
      base = m > 0 ? m : rest;
    }
  } else if (cfg.avecFrais) {
    base = (Number(saisie.montantCapital) || 0) + (Number(saisie.montantFrais) || 0);
  } else {
    const rest = Math.max(0, Number(emp.montantRestant) || 0);
    const m = Math.min(Number(saisie.montant) || rest, rest);
    base = m > 0 ? m : rest;
  }

  if (!saisie.appliquerPenalite) {
    return base;
  }
  const p = calculerPenaliteRetard(
    emp,
    regle,
    ech?.id ?? saisie.echeanceId,
    datePaiement instanceof Date ? datePaiement : new Date(String(datePaiement).slice(0, 10) + 'T12:00:00')
  );
  return base + (p.montant > 0 ? p.montant : 0);
}
