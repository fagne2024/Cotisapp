import { Injectable, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { JournalUtilisateurService } from './journal-utilisateur.service';

interface ModuleRouteInfo {
  code: string;
  libelle: string;
}

@Injectable({ providedIn: 'root' })
export class JournalNavigationService {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly journalApi = inject(JournalUtilisateurService);

  private derniereRoute: string | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;

  demarrer(): void {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.planifierVisite(e.urlAfterRedirects || e.url));
  }

  private planifierVisite(url: string): void {
    if (!this.auth.isAuthenticated() || this.auth.currentRole() === 'SUPERADMIN') {
      return;
    }
    const orgId = this.auth.currentOrgId();
    if (orgId == null) {
      return;
    }
    const path = url.split('?')[0];
    if (!path.includes(`/organisations/${orgId}`)) {
      return;
    }
    if (path === this.derniereRoute) {
      return;
    }
    if (this.timer != null) {
      clearTimeout(this.timer);
    }
    this.timer = setTimeout(() => {
      this.derniereRoute = path;
      const module = resoudreModuleDepuisUrl(path);
      if (!module) {
        return;
      }
      this.journalApi
        .enregistrerEvenement(orgId, {
          typeEvenement: 'MODULE_VISITE',
          action: 'MODULE_VISITE',
          moduleCode: module.code,
          moduleLibelle: module.libelle,
          routePath: path,
          details: `Visite du module ${module.libelle}`,
        })
        .subscribe({ error: () => undefined });
    }, 400);
  }
}

function resoudreModuleDepuisUrl(url: string): ModuleRouteInfo | null {
  if (url.includes('/dashboard')) return { code: 'DASHBOARD', libelle: 'Tableau de bord' };
  if (url.includes('/mon-compte')) return { code: 'MON_COMPTE', libelle: 'Mon compte' };
  if (/\/membres\/\d+/.test(url)) return { code: 'MEMBRES', libelle: 'Fiche membre' };
  if (url.includes('/membres')) return { code: 'MEMBRES', libelle: 'Membres' };
  if (url.includes('/gestion/comptes')) return { code: 'COMPTES', libelle: 'Comptes & Relevés' };
  if (url.includes('/cotisation-mois')) return { code: 'COTISATION', libelle: 'Cotisation' };
  if (url.includes('/suivi-mensuel')) return { code: 'SUIVI_MENSUEL', libelle: 'Suivi mensuel' };
  if (url.includes('/operations/emprunts')) return { code: 'EMPRUNTS', libelle: 'Emprunts' };
  if (url.includes('/remboursements')) return { code: 'REMBOURSEMENT', libelle: 'Remboursement' };
  if (url.includes('/penalite-amende')) return { code: 'PENALITE', libelle: 'Pénalité & Amende' };
  if (url.includes('/gestion/tresorerie') || url.includes('/depense-banque')) {
    return { code: 'TRESORERIE', libelle: 'Trésorerie' };
  }
  if (url.includes('/rapports')) return { code: 'RAPPORTS', libelle: 'Rapports' };
  if (url.includes('/parametrage/')) return { code: 'PARAMETRAGE', libelle: 'Paramétrage' };
  if (url.includes('/gestion/exercices')) return { code: 'EXERCICES', libelle: 'Exercices & PLANAD' };
  if (url.includes('/gestion/utilisateurs')) return { code: 'UTILISATEURS', libelle: 'Utilisateurs & Droits' };
  if (url.includes('/notifications')) return { code: 'NOTIFICATIONS', libelle: 'Notifications' };
  if (url.includes('/mon-profil')) return { code: 'PROFIL', libelle: 'Mon profil' };
  return null;
}
