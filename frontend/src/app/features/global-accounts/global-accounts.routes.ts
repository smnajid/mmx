import { Routes } from '@angular/router';

export const GLOBAL_ACCOUNTS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./global-accounts-list.component').then((m) => m.GlobalAccountsListComponent),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./global-accounts-form.component').then((m) => m.GlobalAccountsFormComponent),
  },
  {
    path: ':client/:currency',
    loadComponent: () =>
      import('./global-accounts-form.component').then((m) => m.GlobalAccountsFormComponent),
  },
];
