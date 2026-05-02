import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'term-orders' },
  {
    path: 'term-orders',
    loadChildren: () =>
      import('./features/term-orders/term-orders.routes').then((m) => m.TERM_ORDERS_ROUTES),
  },
  {
    path: 'oncall-orders',
    loadChildren: () =>
      import('./features/oncall-orders/oncall-orders.routes').then((m) => m.ONCALL_ORDERS_ROUTES),
  },
  { path: '**', redirectTo: 'term-orders' },
];
