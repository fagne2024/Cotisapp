import {
  CompteMembreDto,
  MembreSoldeMembreDto,
  OperationMembreDto,
  TypeCompteMembreApi,
  TypeOperationApi,
} from '../../core/services/membre.service';
import { EmpruntDto } from '../../core/services/emprunt.service';

export type HistFiltreType = 'tous' | 'cotis' | 'mois' | 'remb' | 'pen' | 'amende';

export interface HistOpRow {
  type: HistFiltreType;
  icon: string;
  icoClass: string;
  name: string;
  meta: string;
  amt: string;
  amtClass: string;
}

export function iconeCompte(type: TypeCompteMembreApi): string {
  switch (type) {
    case 'EPARGNE_HEBDO':
    case 'EPARGNE':
      return '📅';
    case 'EPARGNE_MOIS':
      return '📆';
    case 'SOLIDARITE':
      return '🤝';
    case 'PENALITE':
      return '⚠';
    case 'AMENDE':
      return '🚫';
    case 'CUSTOM':
      return '🏷';
    default:
      return '💰';
  }
}

export function classeCompte(type: TypeCompteMembreApi): string {
  switch (type) {
    case 'EPARGNE_HEBDO':
    case 'EPARGNE':
      return 'cc-epargne';
    case 'EPARGNE_MOIS':
      return 'cc-epargne-mois';
    case 'SOLIDARITE':
      return 'cc-solidarite';
    case 'PENALITE':
      return 'cc-penalite';
    case 'AMENDE':
      return 'cc-amende';
    default:
      return 'cc-custom';
  }
}

export function filtrePourOperation(type: TypeOperationApi): HistFiltreType {
  switch (type) {
    case 'COTISATION':
    case 'VERSEMENT':
      return 'cotis';
    case 'COTISATION_MOIS':
      return 'mois';
    case 'REMBOURSEMENT':
    case 'EMPRUNT':
      return 'remb';
    case 'PENALITE':
      return 'pen';
    case 'AMENDE':
      return 'amende';
    default:
      return 'tous';
  }
}

export function libelleOperation(type: TypeOperationApi): string {
  const labels: Record<TypeOperationApi, string> = {
    COTISATION: 'Cotisation hebdomadaire',
    COTISATION_MOIS: 'Cotisation mensuelle',
    VERSEMENT: 'Versement',
    EMPRUNT: 'Emprunt',
    REMBOURSEMENT: 'Remboursement',
    PENALITE: 'Pénalité',
    AMENDE: 'Amende',
    DEPENSE: 'Dépense',
    BANQUE_VERSEMENT: 'Versement banque',
    BANQUE_RETRAIT: 'Retrait banque',
  };
  return labels[type] ?? type;
}

export function operationVersLigne(op: OperationMembreDto): HistOpRow {
  const type = filtrePourOperation(op.typeOperation);
  const montant = Number(op.montant) + Number(op.montantFrais ?? 0);
  const debit =
    op.typeOperation === 'EMPRUNT' ||
    op.typeOperation === 'PENALITE' ||
    op.typeOperation === 'AMENDE' ||
    op.typeOperation === 'DEPENSE';
  const sign = debit ? '−' : '+';
  const amtClass =
    op.typeOperation === 'COTISATION_MOIS'
      ? 'or-c'
      : debit
        ? 'db-c'
        : 'cr-c';
  const dateStr = op.dateOperation
    ? new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(
        new Date(op.dateOperation)
      )
    : '—';
  const metaParts = [dateStr, op.moisAnnee, op.observation].filter(Boolean);
  return {
    type,
    icon:
      op.typeOperation === 'COTISATION_MOIS'
        ? '📅'
        : op.typeOperation === 'REMBOURSEMENT'
          ? '🔄'
          : op.typeOperation === 'AMENDE'
            ? '🚫'
            : op.typeOperation === 'PENALITE'
              ? '⚠'
              : '💰',
    icoClass: op.typeOperation === 'COTISATION_MOIS' ? 'or3' : 'g3',
    name: libelleOperation(op.typeOperation),
    meta: metaParts.join(' · '),
    amt: `${sign}${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(montant)} F`,
    amtClass,
  };
}

export function soldeComptes(comptes: CompteMembreDto[], types: TypeCompteMembreApi[]): number {
  return comptes
    .filter((c) => types.includes(c.typeCompte))
    .reduce((s, c) => s + Number(c.solde ?? 0), 0);
}

/** Recalcule le solde membre côté client (repli si API indisponible). */
export interface CompteCarteAffichage {
  icon: string;
  label: string;
  valeur: number;
  sousTitre: string;
  classe: string;
}

export type StatutSemaineCotis = 'ok' | 'miss' | 'mois' | 'future';

export interface SemaineCotisation {
  label: string;
  statut: StatutSemaineCotis;
}

export interface ResumeMoisLigne {
  label: string;
  valeur: string;
  classe?: string;
}

const ORDRE_COMPTES: TypeCompteMembreApi[] = [
  'EPARGNE_HEBDO',
  'EPARGNE_MOIS',
  'EPARGNE',
  'SOLIDARITE',
  'PENALITE',
  'AMENDE',
  'CUSTOM',
];

function libelleCompteType(type: TypeCompteMembreApi): string {
  switch (type) {
    case 'EPARGNE_HEBDO':
      return 'Épargne hebdo';
    case 'EPARGNE_MOIS':
      return 'Épargne mois';
    case 'EPARGNE':
      return 'Épargne';
    case 'SOLIDARITE':
      return 'Solidarité';
    case 'PENALITE':
      return 'Pénalité';
    case 'AMENDE':
      return 'Amende';
    case 'CUSTOM':
      return 'Compte';
    default:
      return type;
  }
}

function sousTitreCompte(type: TypeCompteMembreApi, totalOps: number, nbOps: number): string {
  if (nbOps === 0) {
    return type === 'PENALITE' || type === 'AMENDE' ? 'RAS' : 'Aucune opération';
  }
  const n = `${nbOps} opération${nbOps > 1 ? 's' : ''}`;
  if (type === 'PENALITE' || type === 'AMENDE') {
    return totalOps > 0 ? n : 'RAS';
  }
  return n;
}

/** Montant comptabilisé dans le cumul affiché sur une carte compte. */
export function montantOperationCumul(op: OperationMembreDto): number {
  const base = Number(op.montant ?? 0);
  const frais = Number(op.montantFrais ?? 0);
  if (op.typeOperation === 'EMPRUNT' || op.typeOperation === 'REMBOURSEMENT') {
    return base + frais;
  }
  return base;
}

function empruntParId(emprunts: EmpruntDto[]): Map<number, EmpruntDto> {
  return new Map(emprunts.map((e) => [e.id, e]));
}

/** Associe une opération au type de compte concerné (cartes 1 à 5). */
function typeComptePourOperation(
  op: OperationMembreDto,
  emprunts: Map<number, EmpruntDto>
): TypeCompteMembreApi | null {
  switch (op.typeOperation) {
    case 'COTISATION':
    case 'VERSEMENT':
      return 'EPARGNE_HEBDO';
    case 'COTISATION_MOIS':
      return 'EPARGNE_MOIS';
    case 'PENALITE':
      return 'PENALITE';
    case 'AMENDE':
      return 'AMENDE';
    case 'REMBOURSEMENT': {
      const emp = op.empruntId != null ? emprunts.get(op.empruntId) : undefined;
      if (emp?.typeEmprunt === 'SOLIDARITE') return 'SOLIDARITE';
      return null;
    }
    default:
      return null;
  }
}

/** Cumul des montants d'opérations par type de compte membre. */
export function sommesOperationsParCompte(
  operations: OperationMembreDto[],
  emprunts: EmpruntDto[]
): Map<TypeCompteMembreApi, { total: number; count: number }> {
  const map = new Map<TypeCompteMembreApi, { total: number; count: number }>();
  const empruntsMap = empruntParId(emprunts);
  for (const op of operations) {
    const type = typeComptePourOperation(op, empruntsMap);
    if (type) {
      const prev = map.get(type) ?? { total: 0, count: 0 };
      map.set(type, {
        total: prev.total + montantOperationCumul(op),
        count: prev.count + 1,
      });
    }
    const partSolidarite = Number(op.montantSolidarite ?? 0);
    if (partSolidarite > 0) {
      const prevSol = map.get('SOLIDARITE') ?? { total: 0, count: 0 };
      map.set('SOLIDARITE', {
        total: prevSol.total + partSolidarite,
        count: prevSol.count + 1,
      });
    }
  }
  return map;
}

/** Capital et frais encore dus sur un emprunt en cours. */
export function encoursEmpruntDetail(emp: EmpruntDto): {
  capitalRestant: number;
  fraisRestant: number;
  total: number;
} {
  const totalEmprunt = Math.max(0, Number(emp.montantTotal) || 0);
  const fraisTotal = Math.max(0, Number(emp.montantFrais) || 0);
  const capitalTotal = Math.max(0, totalEmprunt - fraisTotal);
  const rembourse = Math.max(0, Number(emp.montantRembourse) || 0);
  const fraisRembourse = Math.min(fraisTotal, Math.max(0, rembourse - capitalTotal));
  const capitalRembourse = Math.min(capitalTotal, rembourse - fraisRembourse);
  const fraisRestant = Math.max(0, fraisTotal - fraisRembourse);
  const capitalRestant = Math.max(0, capitalTotal - capitalRembourse);
  const restantApi = Number(emp.montantRestant);
  const total =
    restantApi >= 0 && !Number.isNaN(restantApi)
      ? Math.max(0, restantApi)
      : capitalRestant + fraisRestant;
  return { capitalRestant, fraisRestant, total };
}

/** Somme encours (capital + frais restants) pour les emprunts EN_COURS. */
export function sommeEncoursEmpruntsAvecFrais(empruntsEnCours: EmpruntDto[]): {
  total: number;
  capitalRestant: number;
  fraisRestant: number;
  count: number;
} {
  let capitalRestant = 0;
  let fraisRestant = 0;
  let total = 0;
  for (const emp of empruntsEnCours) {
    const d = encoursEmpruntDetail(emp);
    capitalRestant += d.capitalRestant;
    fraisRestant += d.fraisRestant;
    total += d.total;
  }
  return { total, capitalRestant, fraisRestant, count: empruntsEnCours.length };
}

/** Montant signé pour le détail solde (évite « +− » avec NumberFormat). */
export function formatMontantSolde(montant: number): string {
  const fmt = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(Math.abs(montant));
  if (montant < 0) return `− ${fmt} F`;
  if (montant > 0) return `+ ${fmt} F`;
  return `${fmt} F`;
}

/** Affichage montant sur une carte compte (solde signé). */
export function formatMontantCompte(montant: number): string {
  const fmt = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(Math.abs(montant));
  if (montant < 0) return `−${fmt} FCFA`;
  return `${fmt} FCFA`;
}

/** Épargne = somme des soldes épargne hebdomadaire + mensuelle (hors solidarité). */
export function sommeEpargneHebdoEtMois(comptes: CompteMembreDto[]): number {
  return soldeComptes(comptes, ['EPARGNE_HEBDO', 'EPARGNE_MOIS']);
}

const LIMITE_OPS_RECENTES = 5;

/** Dernières opérations du membre pour un ou plusieurs types. */
export function operationsRecentesParTypes(
  operations: OperationMembreDto[],
  types: TypeOperationApi[],
  limite = LIMITE_OPS_RECENTES
): OperationMembreDto[] {
  return operations.filter((o) => types.includes(o.typeOperation)).slice(0, limite);
}

/** Carte cumul remboursements (compte membre). */
export function buildCarteRemboursements(operations: OperationMembreDto[]): CompteCarteAffichage {
  const remb = operations.filter((o) => o.typeOperation === 'REMBOURSEMENT');
  const total = remb.reduce((s, o) => s + montantOperationCumul(o), 0);
  return {
    icon: '🔄',
    label: 'Remboursements',
    valeur: total,
    sousTitre: remb.length ? `${remb.length} remboursement${remb.length > 1 ? 's' : ''}` : 'Aucun remboursement',
    classe: 'cc-remboursement',
  };
}

/** Carte encours : somme des restes à payer (capital + frais) sur emprunts en cours. */
export function buildCarteEmprunts(empruntsEnCours: EmpruntDto[]): CompteCarteAffichage {
  const { total, fraisRestant, count } = sommeEncoursEmpruntsAvecFrais(empruntsEnCours);
  let sousTitre = 'Aucun encours';
  if (count > 0) {
    sousTitre =
      fraisRestant > 0
        ? `${count} en cours · dont ${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(fraisRestant)} F de frais`
        : `${count} en cours`;
  }
  return {
    icon: '📋',
    label: 'Emprunts',
    valeur: total,
    sousTitre,
    classe: 'cc-emprunt',
  };
}

/** Cartes : cumul des opérations par type de compte (pas le solde courant). */
export function buildComptesCartes(
  comptes: CompteMembreDto[],
  operations: OperationMembreDto[],
  emprunts: EmpruntDto[]
): CompteCarteAffichage[] {
  const cumuls = sommesOperationsParCompte(operations, emprunts);
  return [...comptes]
    .sort(
      (a, b) =>
        (ORDRE_COMPTES.indexOf(a.typeCompte) === -1 ? 99 : ORDRE_COMPTES.indexOf(a.typeCompte)) -
        (ORDRE_COMPTES.indexOf(b.typeCompte) === -1 ? 99 : ORDRE_COMPTES.indexOf(b.typeCompte))
    )
    .map((c) => {
      const cleCumul =
        c.typeCompte === 'EPARGNE' ? 'EPARGNE_HEBDO' : c.typeCompte;
      const agg = cumuls.get(cleCumul) ?? { total: 0, count: 0 };
      return {
        icon: iconeCompte(c.typeCompte),
        label: c.libelle?.trim() || libelleCompteType(c.typeCompte),
        valeur: agg.total,
        sousTitre: sousTitreCompte(c.typeCompte, agg.total, agg.count),
        classe: classeCompte(c.typeCompte),
      };
    });
}

function isoWeek(date: Date): number {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7));
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  return Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
}

export function semainesCotisationMois(
  operations: OperationMembreDto[],
  moisAnnee: string
): SemaineCotisation[] {
  const today = new Date();
  const currentWeek = isoWeek(today);
  const hebdo = operations.filter(
    (o) =>
      o.typeOperation === 'COTISATION' &&
      (o.moisAnnee === moisAnnee || (o.dateOperation?.startsWith(moisAnnee) ?? false))
  );
  const semaines: SemaineCotisation[] = [];
  for (let i = 4; i >= 0; i--) {
    const w = currentWeek - i;
    const label = `S${w}`;
    const payee = hebdo.some((o) => o.dateOperation && isoWeek(new Date(o.dateOperation)) === w);
    let statut: StatutSemaineCotis;
    if (w > currentWeek) statut = 'future';
    else if (payee) statut = 'ok';
    else statut = 'miss';
    semaines.push({ label, statut });
  }
  return semaines;
}

export function alerteCotisationManquee(
  operations: OperationMembreDto[],
  moisAnnee: string
): string | null {
  const pen = operations.find(
    (o) =>
      o.typeOperation === 'PENALITE' &&
      (o.moisAnnee === moisAnnee || (o.dateOperation?.startsWith(moisAnnee) ?? false))
  );
  if (!pen) return null;
  const dateStr = pen.dateOperation
    ? new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(
        new Date(pen.dateOperation)
      )
    : '—';
  const montant = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(
    Number(pen.montant)
  );
  return `Semaine manquée — Pénalité de ${montant} FCFA appliquée le ${dateStr}`;
}

export function resumeMoisLignes(
  operations: OperationMembreDto[],
  moisAnnee: string,
  epargneCumulee: number
): ResumeMoisLigne[] {
  const hebdo = operations.filter(
    (o) =>
      o.typeOperation === 'COTISATION' &&
      (o.moisAnnee === moisAnnee || (o.dateOperation?.startsWith(moisAnnee) ?? false))
  );
  const mois = operations.find(
    (o) => o.typeOperation === 'COTISATION_MOIS' && o.moisAnnee === moisAnnee
  );
  const penalites = operations.filter(
    (o) =>
      o.typeOperation === 'PENALITE' &&
      (o.moisAnnee === moisAnnee || (o.dateOperation?.startsWith(moisAnnee) ?? false))
  );
  const amendes = operations.filter(
    (o) =>
      o.typeOperation === 'AMENDE' &&
      (o.moisAnnee === moisAnnee || (o.dateOperation?.startsWith(moisAnnee) ?? false))
  );

  const montantHebdo = hebdo.length ? Number(hebdo[0].montant) : 0;
  const totalHebdo = hebdo.reduce((s, o) => s + Number(o.montant), 0);
  const fmt = (n: number) =>
    new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(n) + ' F';

  const lignes: ResumeMoisLigne[] = [
    {
      label: 'Cotisations hebdo',
      valeur:
        hebdo.length > 0
          ? `${hebdo.length} × ${fmt(montantHebdo)} = ${fmt(totalHebdo)}`
          : '0 F',
      classe: 'cr-c',
    },
    {
      label: 'Cotisation mensuelle',
      valeur: mois ? fmt(Number(mois.montant)) : '0 F',
      classe: 'or-c',
    },
    {
      label: 'Pénalités',
      valeur: fmt(penalites.reduce((s, o) => s + Number(o.montant), 0)),
      classe: 'db-c',
    },
    {
      label: 'Amendes',
      valeur: fmt(amendes.reduce((s, o) => s + Number(o.montant), 0)),
    },
    {
      label: 'Épargne cumulée',
      valeur: fmt(epargneCumulee),
      classe: 'c-g1',
    },
  ];
  return lignes;
}

export function calculerSoldeMembreLocal(
  membreId: number,
  comptes: CompteMembreDto[],
  emprunts: EmpruntDto[],
  operations: OperationMembreDto[]
): MembreSoldeMembreDto {
  const epargne = sommeEpargneHebdoEtMois(comptes);
  const solidarite = soldeComptes(comptes, ['SOLIDARITE']);
  const empruntsMembre = emprunts.filter((e) => e.membreId === membreId);
  const empruntsTotal = empruntsMembre.reduce((s, e) => s + Number(e.montantTotal ?? 0), 0);
  const fraisEmprunt = empruntsMembre.reduce((s, e) => s + Number(e.montantFrais ?? 0), 0);
  let remboursements = 0;
  let fraisRemboursement = 0;
  for (const op of operations) {
    if (op.typeOperation !== 'REMBOURSEMENT') continue;
    remboursements += Number(op.montant ?? 0);
    fraisRemboursement += Number(op.montantFrais ?? 0);
  }
  const solde = epargne + solidarite;
  return {
    membreId,
    solde,
    epargne,
    solidarite,
    emprunts: empruntsTotal,
    fraisEmprunt,
    remboursements,
    fraisRemboursement,
  };
}
