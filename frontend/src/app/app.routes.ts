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
          import('./features/executed-orders/executed-order-list.component').then(
            (m) => m.ExecutedOrderListComponent
          ),
        data: { workspace: 'term' },
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
          import('./features/executed-orders/executed-order-list.component').then(
            (m) => m.ExecutedOrderListComponent
          ),
        data: { workspace: 'oncall' },
      },
    ],
  },
  {
    path: 'orders/:id',
    loadComponent: () =>
      import('./features/order-details/order-details.component').then((m) => m.OrderDetailsComponent),
  },
  { path: '**', redirectTo: 'oncall/received' },
];
