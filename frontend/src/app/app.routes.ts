import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { mustChangePasswordGuard } from './core/guards/must-change-password.guard';
import { mustSetupTwoFactorGuard } from './core/guards/must-setup-two-factor.guard';
import { orgGuard } from './core/guards/org.guard';
import { roleGuard } from './core/guards/role.guard';
import { orgWorkspaceRoutes } from './core/routes/org-workspace.routes';
import { mobileRoutes } from './features/mobile/mobile.routes';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'changer-mot-de-passe',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/changer-mot-de-passe-initial/changer-mot-de-passe-initial.component').then(
        (m) => m.ChangerMotDePasseInitialComponent
      ),
  },
  {
    path: 'configurer-2fa',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/configurer-2fa/configurer-2fa.component').then((m) => m.Configurer2faComponent),
  },
  {
    path: '403',
    loadComponent: () => import('./features/errors/forbidden/forbidden.component').then((m) => m.ForbiddenComponent),
  },
  {
    path: 'superadmin',
    canActivate: [authGuard, mustChangePasswordGuard, mustSetupTwoFactorGuard, roleGuard],
    data: { roles: ['SUPERADMIN'] },
    loadComponent: () =>
      import('./layout/superadmin-shell/superadmin-shell.component').then((m) => m.SuperadminShellComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/superadmin/superadmin.component').then((m) => m.SuperadminComponent),
      },
      {
        path: 'mon-profil',
        loadComponent: () =>
          import('./features/profil/mon-profil.component').then((m) => m.MonProfilComponent),
      },
      {
        path: 'org/:orgId',
        children: orgWorkspaceRoutes,
      },
    ],
  },
  {
    path: 'organisations/:orgId',
    canActivate: [authGuard, mustChangePasswordGuard, mustSetupTwoFactorGuard, orgGuard],
    loadComponent: () => import('./layout/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: orgWorkspaceRoutes,
  },
  // ── Espace mobile membre ──
  {
    path: 'm/organisations/:orgId',
    canActivate: [authGuard, mustChangePasswordGuard, orgGuard],
    data: { roles: ['MEMBRE'] },
    loadComponent: () =>
      import('./features/mobile/mobile-shell/mobile-shell.component').then(
        (m) => m.MobileShellComponent,
      ),
    children: mobileRoutes,
  },
  { path: '**', redirectTo: 'login' },
];
