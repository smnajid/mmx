import { Routes } from '@angular/router';

export const WIDGET_PLAYGROUND_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./widget-playground.component').then((m) => m.WidgetPlaygroundComponent),
  },
];
