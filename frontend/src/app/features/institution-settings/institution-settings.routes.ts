import { Routes } from '@angular/router';

export const INSTITUTION_SETTINGS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./institution-settings-list.component').then((m) => m.InstitutionSettingsListComponent),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./institution-settings-onboard.component').then((m) => m.InstitutionSettingsOnboardComponent),
  },
  {
    path: ':institutionCode',
    loadComponent: () =>
      import('./institution-settings-detail.component').then((m) => m.InstitutionSettingsDetailComponent),
  },
];
