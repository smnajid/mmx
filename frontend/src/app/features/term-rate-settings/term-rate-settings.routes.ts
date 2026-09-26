import { Routes } from '@angular/router';

export const TERM_RATE_SETTINGS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./term-rate-settings.component').then((m) => m.TermRateSettingsComponent),
  },
];
