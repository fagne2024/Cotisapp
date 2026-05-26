import { Routes } from '@angular/router';

export const mobileRoutes: Routes = [
  { path: '', redirectTo: 'accueil', pathMatch: 'full' },
  {
    path: 'accueil',
    loadComponent: () =>
      import('./home/mobile-home.component').then((m) => m.MobileHomeComponent),
  },
  {
    path: 'compte',
    loadComponent: () =>
      import('./compte/mobile-compte.component').then((m) => m.MobileCompteComponent),
  },
  {
    path: 'cotiser',
    loadComponent: () =>
      import('./cotiser/mobile-cotiser.component').then((m) => m.MobileCotiserComponent),
  },
  {
    path: 'emprunts',
    loadComponent: () =>
      import('./emprunts/mobile-emprunts.component').then((m) => m.MobileEmpruntsComponent),
  },
  {
    path: 'profil',
    loadComponent: () =>
      import('./profil/mobile-profil.component').then((m) => m.MobileProfilComponent),
  },
];
