import { Injectable, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { JournalUtilisateurService } from './journal-utilisateur.service';

interface ModuleRouteInfo {
  code: string;
  libelle: string;
  sousContexte?: string;
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
      const module = resoudreModuleDepuisUrl(path, url);
      if (!module) {
        return;
      }
      const utilisateur = this.auth.nomComplet() || 'Utilisateur';
      const org = this.auth.currentOrgNom();
      let details = `${utilisateur} a ouvert l'écran « ${module.libelle} »`;
      if (module.sousContexte) {
        details += ` (${module.sousContexte})`;
      }
      details += ` — ${path}`;
      if (org) {
        details += ` · ${org}`;
      }
      this.journalApi
        .enregistrerEvenement(orgId, {
          typeEvenement: 'MODULE_VISITE',
          action: 'MODULE_VISITE',
          moduleCode: module.code,
          moduleLibelle: module.libelle,
          routePath: path,
          details,
        })
        .subscribe({ error: () => undefined });
    }, 400);
  }
}

function resoudreModuleDepuisUrl(path: string, fullUrl: string): ModuleRouteInfo | null {
  const q = fullUrl.includes('?') ? new URLSearchParams(fullUrl.split('?')[1]) : null;

  if (path.includes('/dashboard')) {
    return { code: 'DASHBOARD', libelle: 'Tableau de bord' };
  }
  if (path.includes('/mon-compte/rapport')) {
    return { code: 'MON_COMPTE', libelle: 'Mon compte', sousContexte: 'Rapport personnel' };
  }
  if (path.includes('/mon-compte')) {
    return { code: 'MON_COMPTE', libelle: 'Mon compte', sousContexte: 'Fiche et opérations' };
  }
  if (/\/membres\/\d+\/rapport/.test(path)) {
    return { code: 'MEMBRES', libelle: 'Membres', sousContexte: 'Rapport membre' };
  }
  if (/\/membres\/\d+/.test(path)) {
    return { code: 'MEMBRES', libelle: 'Membres', sousContexte: 'Fiche membre' };
  }
  if (path.includes('/membres')) {
    return { code: 'MEMBRES', libelle: 'Membres', sousContexte: 'Liste des membres' };
  }
  if (path.includes('/gestion/comptes')) {
    return { code: 'COMPTES', libelle: 'Comptes & Relevés' };
  }
  if (path.includes('/cotisation-mois')) {
    const t = q?.get('t');
    if (t === 'mois') return { code: 'COTISATION', libelle: 'Cotisation', sousContexte: 'Versement mensuel' };
    if (t === 'historique') return { code: 'COTISATION', libelle: 'Cotisation', sousContexte: 'Historique' };
    return { code: 'COTISATION', libelle: 'Cotisation', sousContexte: 'Versement hebdomadaire (PLANAD)' };
  }
  if (path.includes('/suivi-mensuel')) {
    return { code: 'SUIVI_MENSUEL', libelle: 'Suivi mensuel des cotisations' };
  }
  if (path.includes('/operations/emprunts/suivi')) {
    return { code: 'EMPRUNTS', libelle: 'Emprunts', sousContexte: 'Suivi des emprunts' };
  }
  if (path.includes('/operations/emprunts')) {
    return { code: 'EMPRUNTS', libelle: 'Emprunts', sousContexte: 'Octroi et gestion' };
  }
  if (path.includes('/operations/remboursements') || path.includes('/remboursements/')) {
    const t = q?.get('t');
    const sous =
      t === 'solidarite' ? 'Solidarité' : t === 'caisse' ? 'Caisse' : t === 'etale' ? 'Étalé' : 'Remboursement';
    return { code: 'REMBOURSEMENT', libelle: 'Remboursement', sousContexte: sous };
  }
  if (path.includes('/penalite-amende')) {
    const t = q?.get('t');
    if (t === 'am') return { code: 'AMENDE', libelle: 'Amende', sousContexte: 'Application amende' };
    return { code: 'PENALITE', libelle: 'Pénalité', sousContexte: 'Application pénalité' };
  }
  if (path.includes('/gestion/tresorerie') || path.includes('/depense-banque')) {
    return { code: 'TRESORERIE', libelle: 'Trésorerie', sousContexte: 'Dépenses et banque' };
  }
  if (path.includes('/rapports')) {
    return { code: 'RAPPORTS', libelle: 'Rapports & analyses' };
  }
  if (path.includes('/parametrage/')) {
    return { code: 'PARAMETRAGE', libelle: 'Paramétrage', sousContexte: 'Règles et configuration' };
  }
  if (path.includes('/gestion/exercices')) {
    return { code: 'EXERCICES', libelle: 'Exercices & PLANAD' };
  }
  if (path.includes('/gestion/utilisateurs')) {
    const onglet = q?.get('tab');
    return {
      code: 'UTILISATEURS',
      libelle: 'Utilisateurs & Droits',
      sousContexte: onglet === 'journal' ? 'Journal utilisateurs' : onglet === 'droits' ? 'Droits par profil' : undefined,
    };
  }
  if (path.includes('/notifications')) {
    return { code: 'NOTIFICATIONS', libelle: 'Notifications' };
  }
  if (path.includes('/recap-journee')) {
    return { code: 'RECAP', libelle: 'Récapitulatif de journée (PLANAD)' };
  }
  if (path.includes('/mon-profil')) {
    return { code: 'PROFIL', libelle: 'Mon profil utilisateur' };
  }
  return null;
}
