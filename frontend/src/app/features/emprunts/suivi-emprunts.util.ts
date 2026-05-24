import { EmpruntDto, EcheanceDto, TypeEmprunt } from '../../core/services/emprunt.service';
import { EmpruntsReglesDto } from '../../core/services/regle-operation.service';
import { joursAlertePourTypeEmprunt } from '../../core/util/regle-emprunt.util';
import {
  echeanceEnRetard,
  echeanceProche,
  echeanceRestant,
  empruntEnRetard,
  empruntEcheanceProche,
  joursAvantEcheance,
  premiereEcheanceEnRetard,
  premiereEcheanceProche,
  prochaineEcheanceOuverte,
  progressPctEmprunt,
  referenceEmprunt,
  statutEcheanceUi,
} from '../remboursements/remboursement-emprunt.util';
import { postePourCodeMembre } from '../membres/membres-poste.util';

export type SuiviTab = 'tous' | 'etale' | 'caisse' | 'sol' | 'soldes';
export type SuiviKpiFiltre = 'tous' | 'retard' | 'encours' | 'proches' | 'soldes' | 'montant';

export type SuiviCarteStatut = 'retard' | 'encours' | 'solde' | 'sol';

export interface SuiviEmpruntCard {
  emprunt: EmpruntDto;
  reference: string;
  initials: string;
  avatarColor: string;
  posteSuffix: string | null;
  carteClass: SuiviCarteStatut;
  typeBadgeClass: string;
  typeBadgeLabel: string;
  statutBadgeClass: string;
  statutBadgeLabel: string;
  montantInitial: number;
  montantRembourse: number;
  montantRestant: number;
  progressPct: number;
  progFillClass: string;
  progressColorClass: string;
  alerteHtml: string;
  alerteClass: string;
  footerEch: string;
  enRetard: boolean;
  solde: boolean;
}

export interface SuiviEcheanceRecapLigne {
  empruntId: number;
  membreNom: string;
  posteSuffix: string | null;
  typeEmprunt: TypeEmprunt;
  typeBadgeClass: string;
  typeBadgeLabel: string;
  dateLabel: string;
  dateRetardSuffix: string | null;
  montantDu: number;
  statutBadgeClass: string;
  statutBadgeLabel: string;
  rowClass: string;
  enRetard: boolean;
}

export function typeEmpruntLabel(t: TypeEmprunt): string {
  const m: Record<TypeEmprunt, string> = {
    ETALE: 'Étalé',
    CAISSE: 'Caisse',
    SOLIDARITE: 'Solidarité',
  };
  return m[t] ?? t;
}

export function typeEmpruntBadgeClass(t: TypeEmprunt): string {
  if (t === 'SOLIDARITE') return 'b-blue';
  if (t === 'CAISSE') return 'b-or';
  return 'b-green';
}

export function typeEmpruntIcon(t: TypeEmprunt): string {
  if (t === 'SOLIDARITE') return '🤝';
  if (t === 'CAISSE') return '🏦';
  return '📈';
}

export function empruntEstSolde(emp: EmpruntDto): boolean {
  return emp.statut !== 'EN_COURS' || Number(emp.montantRestant) <= 0;
}

export function empruntSoldeCeMois(emp: EmpruntDto, ref = new Date()): boolean {
  if (!empruntEstSolde(emp)) return false;
  const dc = emp.dateCreation;
  if (!dc || dc.length < 7) return false;
  const y = ref.getFullYear();
  const m = String(ref.getMonth() + 1).padStart(2, '0');
  return dc.startsWith(`${y}-${m}`);
}

export function buildSuiviCard(emp: EmpruntDto, regles?: EmpruntsReglesDto | null): SuiviEmpruntCard {
  const seuilJours = joursAlertePourTypeEmprunt(regles ?? null, emp.typeEmprunt);
  const solde = empruntEstSolde(emp);
  const enRetard = !solde && empruntEnRetard(emp);
  const poste = postePourCodeMembre(emp.codeMembre);
  const posteSuffix =
    poste.kind !== 'simple' ? `(${poste.label})` : null;

  let carteClass: SuiviCarteStatut = 'encours';
  if (solde) carteClass = 'solde';
  else if (enRetard) carteClass = 'retard';
  else if (emp.typeEmprunt === 'SOLIDARITE') carteClass = 'sol';

  const echeances = emp.echeances ?? [];
  const payees = echeances.filter((e) => e.statut === 'PAYE').length;
  const totalEch = echeances.length;

  let footerEch = '';
  if (solde) {
    footerEch = totalEch ? `Éch. ${payees}/${totalEch} · Soldé` : 'Soldé';
  } else if (emp.typeEmprunt === 'CAISSE') {
    footerEch = 'Remboursement unique';
  } else {
    footerEch = totalEch ? `Éch. ${payees}/${totalEch} payée(s)` : '—';
  }

  const { alerteHtml, alerteClass } = buildAlerte(emp, solde, enRetard, seuilJours);

  const frais = Number(emp.montantFrais) || 0;
  const initial =
    emp.typeEmprunt === 'CAISSE'
      ? Number(emp.montantTotal) || 0
      : Math.max(0, (Number(emp.montantTotal) || 0) - frais);

  const pct = progressPctEmprunt(emp);
  const progressColorClass = enRetard ? 're' : emp.typeEmprunt === 'SOLIDARITE' ? 'bl' : emp.typeEmprunt === 'CAISSE' ? 'or' : 'g2';

  return {
    emprunt: emp,
    reference: referenceEmprunt(emp),
    initials: initialsFromNom(emp.membreNom),
    avatarColor: avatarColorFromCode(emp.codeMembre),
    posteSuffix,
    carteClass,
    typeBadgeClass: typeEmpruntBadgeClass(emp.typeEmprunt),
    typeBadgeLabel: `${typeEmpruntIcon(emp.typeEmprunt)} ${typeEmpruntLabel(emp.typeEmprunt)}`,
    statutBadgeClass: solde ? 'b-green' : enRetard ? 'b-red' : emp.typeEmprunt === 'SOLIDARITE' ? 'b-or' : 'b-green',
    statutBadgeLabel: solde ? '✓ Soldé' : enRetard ? '⚠ Retard' : 'En cours',
    montantInitial: initial,
    montantRembourse: Number(emp.montantRembourse) || 0,
    montantRestant: Number(emp.montantRestant) || 0,
    progressPct: pct,
    progFillClass: enRetard ? 'pf-r' : emp.typeEmprunt === 'SOLIDARITE' ? 'pf-b' : emp.typeEmprunt === 'CAISSE' ? 'pf-or' : 'pf-g',
    progressColorClass,
    alerteHtml,
    alerteClass,
    footerEch,
    enRetard,
    solde,
  };
}

function buildAlerte(
  emp: EmpruntDto,
  solde: boolean,
  enRetard: boolean,
  seuilJours: number
): { alerteHtml: string; alerteClass: string } {
  if (solde) {
    const dc = emp.dateCreation ? formatDateFrCourt(emp.dateCreation) : '—';
    return {
      alerteHtml: `✓ Soldé · ${emp.montantRembourse ? 'Remboursements enregistrés' : 'Clôturé'}${dc !== '—' ? ' · ' + dc : ''}`,
      alerteClass: 'alert-muted',
    };
  }
  if (enRetard) {
    const ech = premiereEcheanceEnRetard(emp);
    if (ech) {
      const jours = joursRetard(ech.dateEcheance);
      const montant = echeanceRestant(ech);
      return {
        alerteHtml: `⚠ Échéance ${ech.numero} en retard de ${jours} jour(s) · Montant dû : ${montant.toLocaleString('fr-FR')} F`,
        alerteClass: 'alert-retard',
      };
    }
  }
  const echProche = premiereEcheanceProche(emp, seuilJours);
  if (echProche) {
    const jours = joursAvantEcheance(echProche.dateEcheance);
    const montant = echeanceRestant(echProche);
    const date = formatDateFrCourt(echProche.dateEcheance);
    const delai =
      jours === 0 ? "aujourd'hui" : jours === 1 ? 'demain' : `dans ${jours} jours`;
    return {
      alerteHtml: `⏰ Échéance ${echProche.numero} ${delai} · ${montant.toLocaleString('fr-FR')} F le ${date}`,
      alerteClass: 'alert-proche',
    };
  }
  const prochaine = prochaineEcheanceOuverte(emp);
  if (prochaine) {
    const montant = echeanceRestant(prochaine);
    const date = formatDateFrCourt(prochaine.dateEcheance);
    if (emp.typeEmprunt === 'SOLIDARITE') {
      return {
        alerteHtml: `✓ Sans frais · Prochaine échéance : ${montant.toLocaleString('fr-FR')} F le ${date}`,
        alerteClass: 'alert-info-bl',
      };
    }
    if (emp.typeEmprunt === 'CAISSE' && (emp.montantFrais ?? 0) > 0) {
      return {
        alerteHtml: `⚡ Frais : ${Number(emp.montantFrais).toLocaleString('fr-FR')} F portés par le membre · Prochaine éch. : ${date}`,
        alerteClass: 'alert-info-or',
      };
    }
    return {
      alerteHtml: `✓ À jour · Prochaine échéance : ${montant.toLocaleString('fr-FR')} F le ${date}`,
      alerteClass: 'alert-info-g',
    };
  }
  return { alerteHtml: 'Aucune échéance ouverte.', alerteClass: 'alert-muted' };
}

export function buildEcheancesRecap(
  emprunts: EmpruntDto[],
  regles?: EmpruntsReglesDto | null
): SuiviEcheanceRecapLigne[] {
  const lignes: SuiviEcheanceRecapLigne[] = [];
  for (const emp of emprunts) {
    if (empruntEstSolde(emp)) continue;
    const seuil = joursAlertePourTypeEmprunt(regles ?? null, emp.typeEmprunt);
    const ech =
      premiereEcheanceEnRetard(emp) ??
      premiereEcheanceProche(emp, seuil) ??
      prochaineEcheanceOuverte(emp);
    if (!ech) continue;
    const enRetard = echeanceEnRetard(ech);
    const proche = !enRetard && echeanceProche(ech, seuil);
    const poste = postePourCodeMembre(emp.codeMembre);
    const posteSuffix = poste.kind !== 'simple' ? `(${poste.label})` : null;
    lignes.push({
      empruntId: emp.id,
      membreNom: emp.membreNom,
      posteSuffix,
      typeEmprunt: emp.typeEmprunt,
      typeBadgeClass: typeEmpruntBadgeClass(emp.typeEmprunt),
      typeBadgeLabel: typeEmpruntLabel(emp.typeEmprunt),
      dateLabel: formatDateFrCourt(ech.dateEcheance),
      dateRetardSuffix: enRetard ? `(retard ${joursRetard(ech.dateEcheance)}j)` : null,
      montantDu: echeanceRestant(ech),
      statutBadgeClass: enRetard ? 'b-red' : proche ? 'b-or' : 'b-gray',
      statutBadgeLabel: enRetard ? '⚠ Retard' : proche ? '⏰ Proche' : 'À venir',
      rowClass: enRetard ? 'row-retard' : proche ? 'row-proche' : '',
      enRetard,
    });
  }
  return lignes.sort((a, b) => {
    if (a.enRetard !== b.enRetard) return a.enRetard ? -1 : 1;
    const aProche = a.rowClass === 'row-proche';
    const bProche = b.rowClass === 'row-proche';
    if (aProche !== bProche) return aProche ? -1 : 1;
    return a.membreNom.localeCompare(b.membreNom, 'fr');
  });
}

export function filtrerParTab(cartes: SuiviEmpruntCard[], tab: SuiviTab): SuiviEmpruntCard[] {
  switch (tab) {
    case 'etale':
      return cartes.filter((c) => c.emprunt.typeEmprunt === 'ETALE' && !c.solde);
    case 'caisse':
      return cartes.filter((c) => c.emprunt.typeEmprunt === 'CAISSE' && !c.solde);
    case 'sol':
      return cartes.filter((c) => c.emprunt.typeEmprunt === 'SOLIDARITE' && !c.solde);
    case 'soldes':
      return cartes.filter((c) => c.solde);
    default:
      return cartes.filter((c) => !c.solde);
  }
}

export function filtrerParKpi(
  cartes: SuiviEmpruntCard[],
  kpi: SuiviKpiFiltre | null,
  regles?: EmpruntsReglesDto | null
): SuiviEmpruntCard[] {
  if (!kpi || kpi === 'tous' || kpi === 'montant') return cartes;
  if (kpi === 'retard') return cartes.filter((c) => c.enRetard);
  if (kpi === 'encours') {
    return cartes.filter(
      (c) =>
        !c.solde &&
        !c.enRetard &&
        !empruntEcheanceProche(c.emprunt, joursAlertePourTypeEmprunt(regles ?? null, c.emprunt.typeEmprunt))
    );
  }
  if (kpi === 'proches') {
    return cartes.filter(
      (c) =>
        !c.solde &&
        !c.enRetard &&
        empruntEcheanceProche(c.emprunt, joursAlertePourTypeEmprunt(regles ?? null, c.emprunt.typeEmprunt))
    );
  }
  if (kpi === 'soldes') return cartes.filter((c) => c.solde);
  return cartes;
}

export function compteursTab(cartes: SuiviEmpruntCard[]): Record<SuiviTab, number> {
  const actifs = cartes.filter((c) => !c.solde);
  return {
    tous: actifs.length,
    etale: actifs.filter((c) => c.emprunt.typeEmprunt === 'ETALE').length,
    caisse: actifs.filter((c) => c.emprunt.typeEmprunt === 'CAISSE').length,
    sol: actifs.filter((c) => c.emprunt.typeEmprunt === 'SOLIDARITE').length,
    soldes: cartes.filter((c) => c.solde).length,
  };
}

export function formatFcfaCompact(n: number): string {
  const v = Math.round(n);
  if (v >= 1_000_000) return `${Math.round(v / 100_000) / 10}M`;
  if (v >= 1_000) return `${Math.round(v / 100) / 10}K`;
  return String(v);
}

function initialsFromNom(nom: string): string {
  return nom
    .split(' ')
    .filter(Boolean)
    .map((p) => p[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

function avatarColorFromCode(code: string): string {
  const p = postePourCodeMembre(code).kind;
  if (p === 'president') return '#7c3aed';
  if (p === 'sg' || p === 'sga') return '#1e6fa8';
  if (p === 'tresorier') return '#c0392b';
  return 'var(--g2)';
}

function formatDateFrCourt(iso: string): string {
  const d = new Date(iso + 'T12:00:00');
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(d);
}

function joursRetard(dateEcheance: string, ref = new Date()): number {
  const today = new Date(ref);
  today.setHours(0, 0, 0, 0);
  const d = new Date(dateEcheance + 'T12:00:00');
  d.setHours(0, 0, 0, 0);
  const diff = today.getTime() - d.getTime();
  return Math.max(0, Math.floor(diff / 86_400_000));
}

export function modalEcheanceRows(
  emp: EmpruntDto,
  regles: EmpruntsReglesDto | null = null,
): {
  numero: number;
  dateLabel: string;
  capital: number;
  total: number;
  statutClass: string;
  statutLabel: string;
  rowClass: string;
}[] {
  const frais = Number(emp.montantFrais) || 0;
  const capitalTotal = Math.max(0, (Number(emp.montantTotal) || 0) - frais);
  const n = (emp.echeances ?? []).length || 1;
  const seuil = joursAlertePourTypeEmprunt(regles, emp.typeEmprunt);
  return (emp.echeances ?? [])
    .slice()
    .sort((a, b) => a.numero - b.numero)
    .map((ech) => {
      const ui = statutEcheanceUi(ech, new Date(), seuil);
      const statutLabel =
        ui === 'paye'
          ? '✓ Payée'
          : ui === 'retard'
            ? '⚠ En retard'
            : ui === 'partiel'
              ? 'Partiel'
              : 'À venir';
      const statutClass =
        ui === 'paye' ? 'b-green' : ui === 'retard' ? 'b-red' : ui === 'proche' ? 'b-or' : 'b-gray';
      return {
        numero: ech.numero,
        dateLabel: formatDateFrLong(ech.dateEcheance),
        capital: Math.round(capitalTotal / n),
        total: Number(ech.montantEcheance) || 0,
        statutClass,
        statutLabel,
        rowClass: ui === 'paye' ? 'paid' : ui === 'retard' ? 'retard' : ui === 'proche' ? 'proche' : '',
      };
    });
}

function formatDateFrLong(iso: string): string {
  const d = new Date(iso + 'T12:00:00');
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' }).format(d);
}
