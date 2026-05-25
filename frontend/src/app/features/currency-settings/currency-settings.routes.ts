import { Routes } from '@angular/router';

export const CURRENCY_SETTINGS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./currency-settings-list.component').then((m) => m.CurrencySettingsListComponent),
  },
  {
    path: ':code',
    loadComponent: () =>
      import('./currency-settings-edit.component').then((m) => m.CurrencySettingsEditComponent),
  },
];
