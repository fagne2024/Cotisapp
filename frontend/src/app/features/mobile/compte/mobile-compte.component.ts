import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { MobileDataService } from '../shared/mobile-data.service';
import { formatFcfa } from '../../../core/utils/currency.util';
import { CompteMembreDto, OperationMembreDto } from '../../../core/services/membre.service';

interface CarteCompte {
  icon: string;
  label: string;
  solde: number;
  color: string;
  bg: string;
}

type FiltreOp = 'tous' | 'COTISATION' | 'COTISATION_MOIS' | 'REMBOURSEMENT' | 'EMPRUNT';

@Component({
  selector: 'app-mobile-compte',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './mobile-compte.component.html',
  styleUrl: './mobile-compte.component.scss',
})
export class MobileCompteComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly data = inject(MobileDataService);
  protected readonly fmt = formatFcfa;

  protected readonly filtre = signal<FiltreOp>('tous');
  protected readonly page = signal(0);
  protected readonly PAGE_SIZE = 12;

  protected readonly cartes = computed<CarteCompte[]>(() => {
    const comptes = this.data.comptes();
    const solde = this.data.solde();
    const cartes: CarteCompte[] = [];
    const cfg: Record<string, { icon: string; label: string; color: string; bg: string }> = {
      EPARGNE_HEBDO:  { icon: '📅', label: 'Épargne hebdo',  color: '#1a5c3a', bg: '#e8f5ee' },
      EPARGNE_MOIS:   { icon: '📆', label: 'Épargne mois',   color: '#1e6fa8', bg: '#e8f2fb' },
      SOLIDARITE:     { icon: '🤝', label: 'Solidarité',     color: '#7c3aed', bg: '#ede9fb' },
      PENALITE:       { icon: '⚠️',  label: 'Pénalités',      color: '#c9922a', bg: '#fdf6e7' },
      AMENDE:         { icon: '🚫', label: 'Amendes',        color: '#c0392b', bg: '#fdeaea' },
      CUSTOM:         { icon: '💼', label: 'Autres',         color: '#4a5568', bg: '#f3f4f6' },
    };
    for (const c of comptes) {
      const cf = cfg[c.typeCompte] ?? cfg['CUSTOM'];
      cartes.push({ ...cf, label: c.libelle || cf.label, solde: c.solde });
    }
    if (solde && solde.emprunts > 0) {
      cartes.push({ icon: '💸', label: 'Emprunts en cours', color: '#c0392b', bg: '#fdeaea', solde: solde.emprunts });
    }
    return cartes;
  });

  protected readonly opsFiltrees = computed(() => {
    const f = this.filtre();
    const ops = this.data.operations();
    if (f === 'tous') return ops;
    return ops.filter((o) => o.typeOperation === f);
  });

  protected readonly opsPage = computed(() => {
    const start = this.page() * this.PAGE_SIZE;
    return this.opsFiltrees().slice(start, start + this.PAGE_SIZE);
  });

  protected readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.opsFiltrees().length / this.PAGE_SIZE)),
  );

  protected readonly filtres: { val: FiltreOp; label: string }[] = [
    { val: 'tous', label: 'Toutes' },
    { val: 'COTISATION', label: 'Hebdo' },
    { val: 'COTISATION_MOIS', label: 'Mois' },
    { val: 'REMBOURSEMENT', label: 'Remboursements' },
    { val: 'EMPRUNT', label: 'Emprunts' },
  ];

  ngOnInit(): void {
    const orgId = this.auth.currentOrgId();
    if (orgId) this.data.charger(orgId);
  }

  protected setFiltre(f: FiltreOp): void {
    this.filtre.set(f);
    this.page.set(0);
  }

  protected opIcon(op: OperationMembreDto): string {
    const m: Record<string, string> = {
      COTISATION: '📅', COTISATION_MOIS: '📆', VERSEMENT: '💵',
      EMPRUNT: '💸', REMBOURSEMENT: '✅', PENALITE: '⚠️', AMENDE: '🚫',
    };
    return m[op.typeOperation] ?? '💳';
  }

  protected opPositif(op: OperationMembreDto): boolean {
    return ['COTISATION', 'COTISATION_MOIS', 'VERSEMENT', 'REMBOURSEMENT', 'BANQUE_VERSEMENT'].includes(op.typeOperation);
  }

  protected opLabel(op: OperationMembreDto): string {
    const m: Record<string, string> = {
      COTISATION: 'Cotisation hebdo', COTISATION_MOIS: 'Cotisation mois',
      VERSEMENT: 'Versement', EMPRUNT: 'Emprunt accordé',
      REMBOURSEMENT: 'Remboursement', PENALITE: 'Pénalité', AMENDE: 'Amende',
    };
    return m[op.typeOperation] ?? op.typeOperation;
  }
}
