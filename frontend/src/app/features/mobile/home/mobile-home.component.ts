import { Component, OnInit, computed, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { MobileDataService } from '../shared/mobile-data.service';
import { formatFcfa } from '../../../core/utils/currency.util';
import { OperationMembreDto } from '../../../core/services/membre.service';

interface OpLigne {
  libelle: string;
  montant: number;
  date: string;
  icon: string;
  positif: boolean;
}

@Component({
  selector: 'app-mobile-home',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './mobile-home.component.html',
  styleUrl: './mobile-home.component.scss',
})
export class MobileHomeComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly data = inject(MobileDataService);
  private readonly router = inject(Router);

  protected readonly fmt = formatFcfa;

  protected readonly prenom = computed(() => {
    const n = this.auth.nomComplet();
    return n ? n.split(' ')[0] : 'Membre';
  });

  protected readonly soldeMembre = computed(() => {
    const s = this.data.solde();
    return s ? s.solde : 0;
  });

  protected readonly soldePositif = computed(() => this.soldeMembre() >= 0);

  protected readonly epargne = computed(() => this.data.solde()?.epargne ?? 0);
  protected readonly solidarite = computed(() => this.data.solde()?.solidarite ?? 0);
  protected readonly empruntsTotal = computed(() => this.data.solde()?.emprunts ?? 0);
  protected readonly nbEmpruntsEnCours = computed(() => this.data.empruntsEnCours().length);

  protected readonly opsRecentes = computed<OpLigne[]>(() =>
    this.data.operationsRecentes().map((op) => this.opVersLigne(op)),
  );

  protected readonly heureJour = computed(() => {
    const h = new Date().getHours();
    if (h < 12) return 'Bonjour';
    if (h < 18) return 'Bon après-midi';
    return 'Bonsoir';
  });

  ngOnInit(): void {
    const orgId = this.auth.currentOrgId();
    if (orgId) this.data.charger(orgId);
  }

  protected rafraichir(): void {
    const orgId = this.auth.currentOrgId();
    if (orgId) this.data.rafraichir(orgId);
  }

  protected allerVers(segment: string): void {
    const orgId = this.auth.currentOrgId();
    this.router.navigate([`/m/organisations/${orgId}/${segment}`]);
  }

  private opVersLigne(op: OperationMembreDto): OpLigne {
    const icones: Record<string, string> = {
      COTISATION: '📅',
      COTISATION_MOIS: '📆',
      VERSEMENT: '💵',
      EMPRUNT: '💸',
      REMBOURSEMENT: '✅',
      PENALITE: '⚠️',
      AMENDE: '🚫',
      BANQUE_VERSEMENT: '🏦',
      BANQUE_RETRAIT: '🏦',
    };
    const positif = ['COTISATION', 'COTISATION_MOIS', 'VERSEMENT', 'REMBOURSEMENT', 'BANQUE_VERSEMENT'].includes(
      op.typeOperation,
    );
    const libelles: Record<string, string> = {
      COTISATION: 'Cotisation hebdo',
      COTISATION_MOIS: 'Cotisation mois',
      VERSEMENT: 'Versement',
      EMPRUNT: 'Emprunt accordé',
      REMBOURSEMENT: 'Remboursement',
      PENALITE: 'Pénalité',
      AMENDE: 'Amende',
      BANQUE_VERSEMENT: 'Dépôt banque',
      BANQUE_RETRAIT: 'Retrait banque',
    };
    return {
      libelle: libelles[op.typeOperation] ?? op.typeOperation,
      montant: op.montant,
      date: op.dateOperation,
      icon: icones[op.typeOperation] ?? '💳',
      positif,
    };
  }
}
