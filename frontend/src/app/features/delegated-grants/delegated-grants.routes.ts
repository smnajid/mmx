import { Routes } from '@angular/router';

export const DELEGATED_GRANTS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./delegated-grants-list.component').then((m) => m.DelegatedGrantsListComponent),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./delegated-grants-form.component').then((m) => m.DelegatedGrantsFormComponent),
  },
  {
    path: ':hubInstitutionCode/:clientLegalEntityCode/:currency',
    loadComponent: () =>
      import('./delegated-grants-form.component').then((m) => m.DelegatedGrantsFormComponent),
  },
];
