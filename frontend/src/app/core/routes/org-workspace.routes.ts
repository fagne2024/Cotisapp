import { Routes } from '@angular/router';
import { orgLandingGuard } from '../guards/org-landing.guard';
import { roleGuard } from '../guards/role.guard';

/** Routes fonctionnelles d’un GIE (dashboard, membres, opérations…). */
export const orgWorkspaceRoutes: Routes = [
  /** Anciennes URL ou raccourcis → évite le fallback `**` → login. */
  { path: 'acces', redirectTo: 'gestion/utilisateurs', pathMatch: 'full' },
  { path: 'parametrage', redirectTo: 'parametrage/regles', pathMatch: 'full' },
  { path: 'operations/versement', redirectTo: 'operations/cotisation-mois', pathMatch: 'full' },
  { path: 'operations', redirectTo: 'operations/cotisation-mois', pathMatch: 'full' },
  {
    path: 'dashboard',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE', 'MEMBRE'], sansFicheMembre: true },
    loadComponent: () => import('../../features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'recap-journee',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'RAPPORT_COMPLET' },
    loadComponent: () =>
      import('../../features/recap-journee/recap-journee.component').then((m) => m.RecapJourneeComponent),
  },
  {
    path: 'mon-compte',
    canActivate: [roleGuard],
    data: { roles: ['MEMBRE', 'SUPERADMIN', 'ADMIN_GIE'] },
    loadComponent: () =>
      import('../../features/membres/membre-fiche.component').then((m) => m.MembreFicheComponent),
  },
  {
    path: 'mon-compte/rapport',
    canActivate: [roleGuard],
    data: { roles: ['MEMBRE', 'SUPERADMIN', 'ADMIN_GIE'] },
    loadComponent: () =>
      import('../../features/membres/membre-rapport.component').then((m) => m.MembreRapportComponent),
  },
  {
    path: 'mon-profil',
    canActivate: [roleGuard],
    data: { roles: ['MEMBRE', 'SUPERADMIN', 'ADMIN_GIE'], sansFicheMembre: true },
    loadComponent: () =>
      import('../../features/profil/mon-profil.component').then((m) => m.MonProfilComponent),
  },
  {
    path: 'membres/:membreId/rapport',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'RAPPORT_COMPLET' },
    loadComponent: () =>
      import('../../features/membres/membre-rapport.component').then((m) => m.MembreRapportComponent),
  },
  {
    path: 'membres/:membreId',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'MEMBRE_LISTER' },
    loadComponent: () =>
      import('../../features/membres/membre-fiche.component').then((m) => m.MembreFicheComponent),
  },
  {
    path: 'membres',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'MEMBRE_LISTER' },
    loadComponent: () => import('../../features/membres/membres-page.component').then((m) => m.MembresPageComponent),
  },
  {
    path: 'gestion/comptes',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'SOLDE_ORG' },
    loadComponent: () =>
      import('../../features/comptes/comptes-releves.component').then((m) => m.ComptesRelevesComponent),
  },
  {
    path: 'operations/cotisation-mois',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'OP_COTISATION' },
    loadComponent: () =>
      import('../../features/operations/cotisation-mois/cotisation-mois.component').then(
        (m) => m.CotisationMoisComponent
      ),
  },
  {
    path: 'operations/emprunts',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'OP_EMPRUNT' },
    loadComponent: () => import('../../features/emprunts/emprunts-page.component').then((m) => m.EmpruntsPageComponent),
  },
  {
    path: 'operations/emprunts/suivi',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE', 'MEMBRE'] },
    loadComponent: () =>
      import('../../features/emprunts/suivi-emprunts.component').then((m) => m.SuiviEmpruntsComponent),
  },
  {
    path: 'operations/remboursements',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'OP_REMBOURSEMENT' },
    loadComponent: () =>
      import('../../features/remboursements/remboursement/remboursement.component').then(
        (m) => m.RemboursementComponent
      ),
  },
  {
    path: 'operations/penalite-amende',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'OP_PENALITE' },
    loadComponent: () =>
      import('../../features/operations/penalite-amende/penalite-amende.component').then(
        (m) => m.PenaliteAmendeComponent
      ),
  },
  {
    path: 'gestion/tresorerie',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'OP_DEPENSE' },
    loadComponent: () =>
      import('../../features/operations/depense-banque/depense-banque.component').then(
        (m) => m.DepenseBanqueComponent
      ),
  },
  {
    path: 'operations/depense-banque',
    redirectTo: 'gestion/tresorerie',
    pathMatch: 'full',
  },
  {
    path: 'rapports',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'RAPPORT_COMPLET' },
    loadComponent: () =>
      import('../../features/rapports/rapport-page.component').then((m) => m.RapportPageComponent),
  },
  {
    path: 'parametrage/regles',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'PARAM_REGLES' },
    loadComponent: () =>
      import('../../features/parametrage/parametrage-regles.component').then((m) => m.ParametrageReglesComponent),
  },
  {
    path: 'parametrage/comptes',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'PARAM_REGLES' },
    loadComponent: () =>
      import('../../features/parametrage/parametrage-comptes.component').then(
        (m) => m.ParametrageComptesComponent
      ),
  },
  {
    path: 'parametrage/cloture',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'PARAM_REGLES' },
    loadComponent: () =>
      import('../../features/parametrage/parametrage-cloture.component').then(
        (m) => m.ParametrageClotureComponent
      ),
  },
  {
    path: 'gestion/exercices',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'PARAM_REGLES' },
    loadComponent: () =>
      import('../../features/exercice/gestion-exercices.component').then((m) => m.GestionExercicesComponent),
  },
  {
    path: 'gestion/utilisateurs',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'ADMIN_UTILISATEURS' },
    loadComponent: () =>
      import('../../features/utilisateurs/utilisateurs-droits.component').then(
        (m) => m.UtilisateursDroitsComponent
      ),
  },
  {
    path: 'notifications',
    canActivate: [roleGuard],
    data: { roles: ['MEMBRE', 'SUPERADMIN', 'ADMIN_GIE'] },
    loadComponent: () =>
      import('../../features/notifications/notifications-page.component').then(
        (m) => m.NotificationsPageComponent
      ),
  },
  {
    path: 'gestion/notifications',
    redirectTo: 'notifications',
    pathMatch: 'full',
  },
  {
    path: 'suivi-mensuel',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'RAPPORT_COMPLET' },
    loadComponent: () =>
      import('../../features/suivi-mensuel/suivi-mensuel-list.component').then((m) => m.SuiviMensuelListComponent),
  },
  {
    path: 'remboursements/etale',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'OP_REMBOURSEMENT', variant: 'etale' },
    loadComponent: () =>
      import('../../features/remboursements/remboursement/remboursement.component').then(
        (m) => m.RemboursementComponent
      ),
  },
  {
    path: 'remboursements/solidarite',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'OP_REMBOURSEMENT', variant: 'solidarite' },
    loadComponent: () =>
      import('../../features/remboursements/remboursement/remboursement.component').then(
        (m) => m.RemboursementComponent
      ),
  },
  {
    path: 'remboursements/caisse',
    canActivate: [roleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN_GIE'], gestionBureau: true, action: 'OP_REMBOURSEMENT', variant: 'caisse' },
    loadComponent: () =>
      import('../../features/remboursements/remboursement/remboursement.component').then(
        (m) => m.RemboursementComponent
      ),
  },
  { path: '', pathMatch: 'full', canActivate: [orgLandingGuard], children: [] },
];
