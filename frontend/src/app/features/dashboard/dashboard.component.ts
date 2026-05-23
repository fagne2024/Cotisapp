import { Component, computed, inject, OnInit, OnDestroy, signal, ViewChild, ElementRef } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { DashboardDto, DashboardService } from '../../core/services/dashboard.service';
import { EmpruntService, EmpruntDto } from '../../core/services/emprunt.service';
import { MembreDto } from '../../core/services/membre.service';
import { buildChartViewModel, ligneBureau, operationDashboardVersLigne } from './dashboard.util';
import { formatFcfa } from '../../core/utils/currency.util';
import { forkJoin, interval, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  private readonly empruntService = inject(EmpruntService);
  private readonly dashboardService = inject(DashboardService);

  readonly alertVisible = signal(true);
  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly dashboard = signal<DashboardDto | null>(null);
  readonly emprunts = signal<EmpruntDto[]>([]);

  readonly selectedYear = signal(new Date().getFullYear());
  readonly selectedMonth = signal(new Date().getMonth() + 1);
  readonly autoRefreshEnabled = signal(true);
  readonly autoRefreshInterval = signal(5 * 60 * 1000); // 5 minutes

  private autoRefreshSubscription: Subscription | null = null;

  readonly chartView = computed(() =>
    buildChartViewModel(
      this.dashboard()?.evolutionCotisations,
      this.selectedYear()
    )
  );

  readonly monthLabel = computed(() => {
    const months = ['Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Jun', 'Jul', 'Aoû', 'Sep', 'Oct', 'Nov', 'Déc'];
    return months[this.selectedMonth() - 1] ?? '';
  });

  readonly hoveredBarMonth = signal<number | null>(null);

  readonly ligneBureau = ligneBureau;

  readonly enCours = computed(() => this.emprunts().filter((e) => e.statut === 'EN_COURS'));

  readonly overdueEmprunts = computed(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return this.enCours().filter((emp) => this.empruntHasOverdue(emp, today));
  });

  readonly topEmpruntsForCard = computed(() => this.enCours().slice(0, 2));

  readonly operationsRecentes = computed(() => {
    const ops = this.dashboard()?.operationsRecentes ?? [];
    return ops.map(operationDashboardVersLigne);
  });

  readonly greetingName = computed(() => {
    const name = this.auth.nomComplet();
    return (name.split(' ')[0] || 'vous').replace(/^./, (c) => c.toUpperCase());
  });

  readonly subline = computed(() => {
    const org = this.auth.currentOrgNom() ?? 'Organisation';
    const now = new Date();
    const week = this.isoWeek(now);
    const dateStr = new Intl.DateTimeFormat('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(now);
    return `${org} · Semaine ${week} · ${dateStr}`;
  });

  ngOnInit(): void {
    this.loadDashboardData();
    this.setupAutoRefresh();
  }

  ngOnDestroy(): void {
    this.autoRefreshSubscription?.unsubscribe();
  }

  private loadDashboardData(): void {
    const id = this.auth.currentOrgId();
    if (id == null) return;
    forkJoin({
      dashboard: this.dashboardService.obtenir(id),
      emprunts: this.empruntService.lister(id),
    }).subscribe({
      next: ({ dashboard, emprunts }) => {
        this.dashboard.set(dashboard);
        this.emprunts.set(emprunts);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  private setupAutoRefresh(): void {
    this.autoRefreshSubscription = interval(this.autoRefreshInterval())
      .pipe(
        filter(() => this.autoRefreshEnabled())
      )
      .subscribe(() => {
        this.loadDashboardData();
      });
  }

  toggleAutoRefresh(): void {
    this.autoRefreshEnabled.update((v) => !v);
  }

  onChartBarHover(mois: number): void {
    this.hoveredBarMonth.set(mois);
  }

  onChartBarLeave(): void {
    this.hoveredBarMonth.set(null);
  }

  onChartBarClick(mois: number, annee: number): void {
    this.selectedMonth.set(mois);
    this.selectedYear.set(annee);
  }

  soldeCaisse(): number {
    return Number(this.dashboard()?.soldeCaisse ?? 0);
  }

  soldeSolidarite(): number {
    return Number(this.dashboard()?.soldeSolidarite ?? 0);
  }

  soldeBanque(): number {
    return Number(this.dashboard()?.soldeBanque ?? 0);
  }

  nbMembresActifs(): number {
    return this.dashboard()?.nbMembresActifs ?? 0;
  }

  nbMembresBureau(): number {
    return this.dashboard()?.nbMembresBureau ?? 0;
  }

  nbMembresSimples(): number {
    return this.dashboard()?.nbMembresSimples ?? 0;
  }

  bureau(): MembreDto[] {
    return this.dashboard()?.bureau ?? [];
  }

  dismissAlert(): void {
    this.alertVisible.set(false);
  }

  changeMonth(offset: number): void {
    let newMonth = this.selectedMonth() + offset;
    let newYear = this.selectedYear();

    if (newMonth < 1) {
      newMonth = 12;
      newYear--;
    } else if (newMonth > 12) {
      newMonth = 1;
      newYear++;
    }

    this.selectedMonth.set(newMonth);
    this.selectedYear.set(newYear);
  }

  changeYear(offset: number): void {
    this.selectedYear.update((y) => y + offset);
  }

  resetToToday(): void {
    const today = new Date();
    this.selectedYear.set(today.getFullYear());
    this.selectedMonth.set(today.getMonth() + 1);
  }

  refreshData(): void {
    this.loading.set(true);
    const id = this.auth.currentOrgId();
    if (id == null) return;

    this.dashboardService.obtenir(id).subscribe({
      next: (data) => {
        this.dashboard.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  exportPDF(): void {
    const filename = `Dashboard-${new Date().toISOString().split('T')[0]}.pdf`;
    const content = this.generatePDFContent();

    // Open print dialog (or save to PDF via browser)
    const printWindow = window.open('', '', 'width=900,height=1200');
    if (printWindow) {
      printWindow.document.write(content);
      printWindow.document.close();
      setTimeout(() => {
        printWindow.print();
      }, 250);
    }
  }

  private generatePDFContent(): string {
    const now = new Date().toLocaleDateString('fr-FR');
    const org = this.auth.currentOrgNom() ?? 'Organisation';

    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <title>Dashboard ${now}</title>
        <style>
          body { font-family: Arial, sans-serif; margin: 20px; color: #333; }
          h1 { color: #1a5c3a; border-bottom: 3px solid #1a5c3a; padding-bottom: 10px; }
          h2 { color: #2d7a52; margin-top: 20px; font-size: 16px; }
          .kpi-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin: 20px 0; }
          .kpi-card { border: 1px solid #ddd; border-radius: 8px; padding: 15px; background: #f9f9f9; }
          .kpi-label { font-size: 12px; color: #666; }
          .kpi-value { font-size: 20px; font-weight: bold; color: #1a5c3a; }
          table { width: 100%; border-collapse: collapse; margin-top: 10px; }
          th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
          th { background: #e8f5ee; color: #1a5c3a; }
          .page-break { page-break-after: always; margin: 20px 0; }
          small { color: #999; }
        </style>
      </head>
      <body>
        <h1>📊 Tableau de Bord - ${org}</h1>
        <p><small>Généré le ${now} par ${this.greetingName()}</small></p>

        <h2>Indicateurs Clés</h2>
        <div class="kpi-grid">
          <div class="kpi-card">
            <div class="kpi-label">Solde Caisse</div>
            <div class="kpi-value">${this.formatFcfaUnit(this.soldeCaisse())} F</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Fonds Solidarité</div>
            <div class="kpi-value">${this.formatFcfaUnit(this.soldeSolidarite())} F</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Emprunts En Cours</div>
            <div class="kpi-value">${this.enCours().length}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Membres Actifs</div>
            <div class="kpi-value">${this.nbMembresActifs()}</div>
          </div>
        </div>

        <div class="page-break"></div>
        <h2>Soldes des Comptes</h2>
        <table>
          <tr>
            <th>Type de Compte</th>
            <th>Solde</th>
          </tr>
          <tr>
            <td>Caisse</td>
            <td>${this.formatFcfaUnit(this.soldeCaisse())} F</td>
          </tr>
          <tr>
            <td>Banque</td>
            <td>${this.formatFcfaUnit(this.soldeBanque())} F</td>
          </tr>
          <tr>
            <td>Solidarité</td>
            <td>${this.formatFcfaUnit(this.soldeSolidarite())} F</td>
          </tr>
        </table>

        <h2>Emprunts Actifs (Top 5)</h2>
        <table>
          <tr>
            <th>Membre</th>
            <th>Type</th>
            <th>Montant</th>
            <th>Remboursé</th>
            <th>Statut</th>
          </tr>
          ${this.topEmpruntsForCard()
            .map(
              (e) => `
            <tr>
              <td>${e.membreNom}</td>
              <td>${this.typeLabel(e.typeEmprunt)}</td>
              <td>${this.formatFcfaUnit(e.montantTotal)} F</td>
              <td>${this.formatFcfaUnit(e.montantRembourse)} F (${this.progressPct(e)}%)</td>
              <td>${this.empruntHasOverdue(e) ? '⚠ Retard' : 'En cours'}</td>
            </tr>
          `
            )
            .join('')}
        </table>

        <p style="text-align: center; margin-top: 40px; color: #999; font-size: 12px;">
          <small>Document généré automatiquement • Confidentiel</small>
        </p>
      </body>
      </html>
    `;
  }

  overdueSummary(): string {
    const list = this.overdueEmprunts();
    if (list.length === 0) return '';
    const head = list
      .slice(0, 2)
      .map((e) => `${e.membreNom} (${this.formatFcfa(e.montantRestant)})`)
      .join(' et ');
    const extra = list.length > 2 ? ` et ${list.length - 2} autre(s)` : '';
    return head + extra;
  }

  readonly formatFcfa = formatFcfa;

  formatFcfaUnit(n: number): string {
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(n);
  }

  typeLabel(t: string): string {
    const m: Record<string, string> = { ETALE: 'Étalé', SOLIDARITE: 'Solidarité', CAISSE: 'Caisse' };
    return m[t] ?? t;
  }

  progressPct(emp: EmpruntDto): number {
    if (!emp.montantTotal) return 0;
    return Math.min(100, Math.round((emp.montantRembourse / emp.montantTotal) * 100));
  }

  empruntHasOverdue(emp: EmpruntDto, today: Date = new Date()): boolean {
    const t = new Date(today);
    t.setHours(0, 0, 0, 0);
    return (emp.echeances ?? []).some((ech) => {
      if (ech.statut === 'PAYE') return false;
      const d = new Date(ech.dateEcheance);
      d.setHours(0, 0, 0, 0);
      return d < t;
    });
  }

  private isoWeek(d: Date): number {
    const x = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    const day = x.getUTCDay() || 7;
    x.setUTCDate(x.getUTCDate() + 4 - day);
    const y = new Date(Date.UTC(x.getUTCFullYear(), 0, 1));
    return Math.ceil((+x - +y) / 86400000 / 7);
  }
}
