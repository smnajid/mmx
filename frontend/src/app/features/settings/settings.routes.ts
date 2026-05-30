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
];
