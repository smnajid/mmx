import { Routes } from '@angular/router';

export const SETTINGS_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'currencies' },
  {
    path: 'currencies',
    loadChildren: () =>
      import('../currency-settings/currency-settings.routes').then((m) => m.CURRENCY_SETTINGS_ROUTES),
  },
  {
    path: 'institutions',
    loadChildren: () =>
      import('../institution-settings/institution-settings.routes').then(
        (m) => m.INSTITUTION_SETTINGS_ROUTES
      ),
  },
  {
    path: 'term-rates',
    loadChildren: () =>
      import('../term-rate-settings/term-rate-settings.routes').then((m) => m.TERM_RATE_SETTINGS_ROUTES),
  },
  {
    path: 'oncall-rates',
    loadChildren: () =>
      import('../oncall-rate-settings/oncall-rate-settings.routes').then((m) => m.ONCALL_RATE_SETTINGS_ROUTES),
  },
  {
    path: 'delegated-grants',
    loadChildren: () =>
      import('../delegated-grants/delegated-grants.routes').then((m) => m.DELEGATED_GRANTS_ROUTES),
  },
];
