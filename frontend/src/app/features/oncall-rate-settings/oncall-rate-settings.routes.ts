import { Routes } from '@angular/router';

export const ONCALL_RATE_SETTINGS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./oncall-rate-settings.component').then((m) => m.OnCallRateSettingsComponent),
  },
];
