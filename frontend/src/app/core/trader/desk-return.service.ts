import { Injectable, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

const STORAGE_KEY = 'mmx.desk-return-url';
const DEFAULT_DESK_URL = '/oncall/received';
const DESK_PATH_RE = /^\/(oncall|term)\/(received|assigned|executed)$/;

@Injectable({ providedIn: 'root' })
export class DeskReturnService {
  private readonly router = inject(Router);

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.rememberIfDesk(e.urlAfterRedirects));
  }

  getReturnUrl(): string {
    if (typeof sessionStorage === 'undefined') {
      return DEFAULT_DESK_URL;
    }
    const stored = sessionStorage.getItem(STORAGE_KEY);
    return stored && this.isDeskPath(stored) ? stored : DEFAULT_DESK_URL;
  }

  isDeskPath(path: string): boolean {
    return DESK_PATH_RE.test(path.split('?')[0]);
  }

  isSettingsPath(path: string): boolean {
    return path.split('?')[0].startsWith('/settings');
  }

  private rememberIfDesk(url: string): void {
    const pathOnly = url.split('?')[0];
    if (this.isDeskPath(pathOnly) && typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem(STORAGE_KEY, pathOnly);
    }
  }
}
