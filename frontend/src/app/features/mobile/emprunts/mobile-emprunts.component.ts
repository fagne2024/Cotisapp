import { Component, OnInit, computed, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { MobileDataService } from '../shared/mobile-data.service';
import { formatFcfa } from '../../../core/utils/currency.util';
import { EmpruntDto } from '../../../core/services/emprunt.service';

@Component({
  selector: 'app-mobile-emprunts',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './mobile-emprunts.component.html',
  styleUrl: './mobile-emprunts.component.scss',
})
export class MobileEmpruntsComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly data = inject(MobileDataService);
  protected readonly fmt = formatFcfa;

  protected readonly empruntsEnCours = computed(() =>
    this.data.emprunts().filter((e) => e.statut === 'EN_COURS' || e.statut === 'RETARD'),
  );

  protected readonly empruntsClos = computed(() =>
    this.data.emprunts().filter((e) => e.statut === 'CLOS'),
  );

  protected readonly totalRestant = computed(() =>
    this.empruntsEnCours().reduce((sum, e) => sum + (e.montantRestant ?? 0), 0),
  );

  ngOnInit(): void {
    const orgId = this.auth.currentOrgId();
    if (orgId) this.data.charger(orgId);
  }

  protected pct(e: EmpruntDto): number {
    if (!e.montantTotal || e.montantTotal === 0) return 0;
    return Math.round((e.montantRembourse / e.montantTotal) * 100);
  }

  protected typeLabel(e: EmpruntDto): string {
    const m: Record<string, string> = {
      ETALE: 'Étalé', SOLIDARITE: 'Solidarité', CAISSE: 'Caisse',
    };
    return m[e.typeEmprunt] ?? e.typeEmprunt;
  }

  protected enRetard(e: EmpruntDto): boolean {
    return e.statut === 'RETARD';
  }
}
