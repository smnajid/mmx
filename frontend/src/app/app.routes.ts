import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'oncall/received' },
  {
    path: 'term',
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'received' },
      {
        path: 'received',
        loadChildren: () =>
          import('./features/term-orders/term-orders.routes').then((m) => m.TERM_ORDERS_ROUTES),
      },
      {
        path: 'assigned',
        loadComponent: () =>
          import('./features/assigned-orders/assigned-order-list.component').then(
            (m) => m.AssignedOrderListComponent
          ),
        data: { workspace: 'term' },
      },
      {
        path: 'executed',
        loadComponent: () =>
          import('./features/term-orders/term-executed-order-list.component').then(
            (m) => m.TermExecutedOrderListComponent
          ),
      },
    ],
  },
  {
    path: 'oncall',
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'received' },
      {
        path: 'received',
        loadChildren: () =>
          import('./features/oncall-orders/oncall-orders.routes').then((m) => m.ONCALL_ORDERS_ROUTES),
      },
      {
        path: 'assigned',
        loadComponent: () =>
          import('./features/assigned-orders/assigned-order-list.component').then(
            (m) => m.AssignedOrderListComponent
          ),
        data: { workspace: 'oncall' },
      },
      {
        path: 'executed',
        loadComponent: () =>
          import('./features/oncall-orders/oncall-executed-order-list.component').then(
            (m) => m.OnCallExecutedOrderListComponent
          ),
      },
    ],
  },
  {
    path: 'settings',
    loadComponent: () =>
      import('./features/settings/settings-shell.component').then((m) => m.SettingsShellComponent),
    loadChildren: () =>
      import('./features/settings/settings.routes').then((m) => m.SETTINGS_ROUTES),
  },
  {
    path: 'orders/:id',
    loadComponent: () =>
      import('./features/order-details/order-details.component').then((m) => m.OrderDetailsComponent),
  },
  { path: '**', redirectTo: 'oncall/received' },
];
