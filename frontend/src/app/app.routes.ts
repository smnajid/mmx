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
  {
    path: 'assigned-orders',
    loadChildren: () =>
      import('./features/assigned-orders/assigned-orders.routes').then((m) => m.ASSIGNED_ORDERS_ROUTES),
  },
  {
    path: 'orders/:orderId',
    loadComponent: () =>
      import('./features/order-detail/order-detail.component').then((m) => m.OrderDetailComponent),
  },
  { path: '**', redirectTo: 'term-orders' },
];
