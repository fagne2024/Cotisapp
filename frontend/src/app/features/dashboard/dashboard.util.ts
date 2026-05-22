import { MembreDto, OperationMembreDto, TypeOperationApi } from '../../core/services/membre.service';
import { PosteKind, PosteMeta, postePourMembre } from '../membres/membres-poste.util';
import { libelleOperation } from '../membres/membre-fiche.util';

export interface DashboardOpRow {
  icon: string;
  icoClass: string;
  name: string;
  meta: string;
  amt: string;
  amtClass: string;
}

function estCreditOrg(type: TypeOperationApi): boolean {
  return (
    type === 'COTISATION' ||
    type === 'COTISATION_MOIS' ||
    type === 'VERSEMENT' ||
    type === 'REMBOURSEMENT' ||
    type === 'BANQUE_VERSEMENT'
  );
}

function iconeOperation(type: TypeOperationApi): string {
  switch (type) {
    case 'COTISATION':
    case 'VERSEMENT':
      return '💰';
    case 'COTISATION_MOIS':
      return '📅';
    case 'EMPRUNT':
      return '📋';
    case 'REMBOURSEMENT':
      return '🔄';
    case 'PENALITE':
    case 'AMENDE':
      return '⚠';
    case 'DEPENSE':
      return '💸';
    case 'BANQUE_VERSEMENT':
    case 'BANQUE_RETRAIT':
      return '🏛';
    default:
      return '💰';
  }
}

function classeIco(type: TypeOperationApi): string {
  switch (type) {
    case 'EMPRUNT':
      return 're2';
    case 'REMBOURSEMENT':
      return 'bl2';
    case 'COTISATION_MOIS':
      return 'or3';
    case 'PENALITE':
    case 'AMENDE':
      return 're2';
    default:
      return 'g3';
  }
}

export function operationDashboardVersLigne(op: OperationMembreDto): DashboardOpRow {
  const montant = Number(op.montant) + Number(op.montantFrais ?? 0);
  const credit = estCreditOrg(op.typeOperation);
  const sign = credit ? '+' : '−';
  const libelle = libelleOperation(op.typeOperation);
  const membre = op.membreNom?.trim();
  const name = membre ? `${libelle} — ${membre}` : libelle;
  const dateStr = op.dateOperation
    ? new Intl.DateTimeFormat('fr-FR', {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      }).format(new Date(op.dateOperation + 'T12:00:00'))
    : '—';
  const metaParts = [dateStr, op.moisAnnee, op.observation].filter(Boolean);
  const amtClass =
    op.typeOperation === 'COTISATION_MOIS' ? 'or-c' : credit ? 'cr-c' : 'db-c';
  return {
    icon: iconeOperation(op.typeOperation),
    icoClass: classeIco(op.typeOperation),
    name,
    meta: metaParts.join(' · '),
    amt: `${sign}${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(montant)} F`,
    amtClass,
  };
}

export function initialesNom(nomComplet: string): string {
  const parts = nomComplet.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/** Classes avatar / badge bureau (alignées sur la maquette dashboard). */
export function bureauStyles(kind: PosteKind): { av: string; badge: string } {
  switch (kind) {
    case 'president':
    case 'vice_president':
      return { av: 'purp', badge: 'purp-b' };
    case 'sg':
      return { av: 'bl', badge: 'bl-b' };
    case 'sga':
      return { av: 'or', badge: 'or-b' };
    case 'tresorier':
      return { av: 'g1', badge: 'g-b' };
    case 'superviseur':
      return { av: 're', badge: 're-b' };
    default:
      return { av: 'g1', badge: 'g-b' };
  }
}

export function posteBureau(membre: { codeMembre: string; poste?: string | null }) {
  return postePourMembre(membre.codeMembre, membre.poste as Parameters<typeof postePourMembre>[1]);
}

export interface BureauLigne {
  initiales: string;
  poste: PosteMeta;
  avClass: string;
  badgeClass: string;
}

const MOIS_LABELS = ['Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Jun', 'Jul', 'Aoû', 'Sep', 'Oct', 'Nov', 'Déc'] as const;

export interface ChartBarColumn {
  mois: number;
  label: string;
  cotisations: number;
  objectif: number;
  cotisPct: number;
  objPct: number;
  atteintObjectif: boolean;
  tooltip: string;
}

export interface ChartYTick {
  value: number;
  label: string;
  pct: number;
}

export interface ChartViewModel {
  columns: ChartBarColumn[];
  yTicks: ChartYTick[];
  maxValue: number;
  totalCotisations: number;
  totalObjectif: number;
  annee: number;
}

export function buildChartViewModel(
  stats: { mois: number; montantCotisations: number; objectif: number }[] | undefined,
  annee: number
): ChartViewModel {
  const rows = (stats?.length ? stats : MOIS_LABELS.map((_, i) => ({
    mois: i + 1,
    montantCotisations: 0,
    objectif: 0,
  }))).slice(0, 12);

  let maxValue = 0;
  let totalCotisations = 0;
  let totalObjectif = 0;

  for (const r of rows) {
    const c = Number(r.montantCotisations) || 0;
    const o = Number(r.objectif) || 0;
    totalCotisations += c;
    totalObjectif += o;
    maxValue = Math.max(maxValue, c, o);
  }

  if (maxValue <= 0) {
    maxValue = 1;
  }

  const yTicks = buildYTicks(maxValue);

  const columns: ChartBarColumn[] = rows.map((r) => {
    const cotisations = Number(r.montantCotisations) || 0;
    const objectif = Number(r.objectif) || 0;
    const cotisPct = Math.max(2, Math.round((cotisations / maxValue) * 100));
    const objPct = Math.max(2, Math.round((objectif / maxValue) * 100));
    const label = MOIS_LABELS[r.mois - 1] ?? String(r.mois);
    const fmt = (n: number) =>
      new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(n);
    return {
      mois: r.mois,
      label,
      cotisations,
      objectif,
      cotisPct: cotisations > 0 ? cotisPct : 0,
      objPct: objectif > 0 ? objPct : 0,
      atteintObjectif: objectif > 0 && cotisations >= objectif,
      tooltip: `${label} — Cotisations : ${fmt(cotisations)} F · Objectif : ${fmt(objectif)} F`,
    };
  });

  return {
    columns,
    yTicks,
    maxValue,
    totalCotisations,
    totalObjectif,
    annee,
  };
}

function buildYTicks(maxValue: number): ChartYTick[] {
  const steps = 4;
  const ticks: ChartYTick[] = [];
  for (let i = steps; i >= 0; i--) {
    const value = (maxValue * i) / steps;
    ticks.push({
      value,
      label: formatAxisValue(value),
      pct: (i / steps) * 100,
    });
  }
  return ticks;
}

function formatAxisValue(n: number): string {
  if (n >= 1_000_000) {
    return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1)} M`;
  }
  if (n >= 1_000) {
    return `${Math.round(n / 1_000)} k`;
  }
  return String(Math.round(n));
}

export function ligneBureau(membre: MembreDto): BureauLigne {
  const poste = posteBureau(membre);
  const styles = bureauStyles(poste.kind);
  return {
    initiales: initialesNom(membre.nomComplet),
    poste,
    avClass: styles.av,
    badgeClass: styles.badge,
  };
}
