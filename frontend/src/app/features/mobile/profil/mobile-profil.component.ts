import { Component, OnInit, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { MobileDataService } from '../shared/mobile-data.service';

const AV_COLORS = ['#7c3aed', '#1e6fa8', '#1a5c3a', '#c9922a', '#c0392b', '#2d7a52'];

@Component({
  selector: 'app-mobile-profil',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mobile-profil.component.html',
  styleUrl: './mobile-profil.component.scss',
})
export class MobileProfilComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly data = inject(MobileDataService);

  protected readonly membre = computed(() => this.data.membre());

  protected readonly initiales = computed(() => {
    const n = this.auth.nomComplet();
    if (!n) return '?';
    const parts = n.trim().split(/\s+/);
    return parts.length >= 2
      ? (parts[0][0] + parts[1][0]).toUpperCase()
      : n.substring(0, 2).toUpperCase();
  });

  protected readonly avatarColor = computed(() => {
    const id = this.auth.currentMembreId() ?? 0;
    return AV_COLORS[id % AV_COLORS.length];
  });

  protected readonly poste = computed(() => {
    const m = this.data.membre();
    if (!m?.poste || m.poste === 'SIMPLE') return 'Membre simple';
    return m.poste.replace(/_/g, ' ');
  });

  ngOnInit(): void {
    const orgId = this.auth.currentOrgId();
    if (orgId) this.data.charger(orgId);
  }

  protected deconnecter(): void {
    this.data.reset();
    this.auth.logout();
  }
}
