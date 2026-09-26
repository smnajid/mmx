import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TraderContextService } from './trader-context.service';

/** Blocks desk queue routes for ClientRepresentative sessions; sends them to Settings. */
export const traderDeskGuard: CanActivateFn = () => {
  const user = inject(TraderContextService);
  const router = inject(Router);
  if (user.isClientRepresentative()) {
    return router.parseUrl('/settings');
  }
  return true;
};
