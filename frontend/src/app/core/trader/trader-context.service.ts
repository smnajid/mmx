import { Injectable, signal } from '@angular/core';
import type { UserRole } from '../api/session-api.service';

const USER_ID_KEY = 'mmx-user-id';
const LEGACY_USER_ID_KEY = 'mmx-trader-id';
const ACTIVE_SCOPE_KEY = 'mmx-active-scope';
const HELD_SCOPES_KEY = 'mmx-held-scopes';

export interface UserScope {
  legalEntityCode: string;
  role: UserRole;
}

const DEFAULT_USER_ID = 'demo-trader';

/** Demo held scopes for the POC shell switcher (mirrors typical LODH deployment). */
const DEFAULT_HELD_SCOPES: UserScope[] = [
  { legalEntityCode: 'LOC', role: 'TRADER' },
  { legalEntityCode: 'PAR', role: 'CLIENT_REPRESENTATIVE' },
];

const DEFAULT_ACTIVE_SCOPE: UserScope = DEFAULT_HELD_SCOPES[0];

/**
 * MMXUser identity for `X-User-Id` on trader/settings API calls.
 * Active `(legalEntityCode, role)` is session-carried on the server; the shell
 * persists a local mirror for navigation gating and the scope switcher.
 */
@Injectable({ providedIn: 'root' })
export class TraderContextService {
  readonly userId = signal<string>(this.readStoredUserId());
  /** @deprecated Use `userId` — kept for gradual migration in templates. */
  readonly traderId = this.userId;

  readonly heldScopes = signal<UserScope[]>(this.readHeldScopes());
  readonly activeScope = signal<UserScope>(this.readActiveScope());

  setUserId(id: string): void {
    const trimmed = id.trim();
    if (trimmed.length === 0) {
      return;
    }
    sessionStorage.setItem(USER_ID_KEY, trimmed);
    this.userId.set(trimmed);
  }

  /** @deprecated Use `setUserId`. */
  setTraderId(id: string): void {
    this.setUserId(id);
  }

  bindActiveScope(scope: UserScope): void {
    sessionStorage.setItem(ACTIVE_SCOPE_KEY, JSON.stringify(scope));
    this.activeScope.set(scope);
  }

  scopeKey(scope: UserScope): string {
    return `${scope.legalEntityCode}:${scope.role}`;
  }

  isTrader(): boolean {
    return this.activeScope().role === 'TRADER';
  }

  isClientRepresentative(): boolean {
    return this.activeScope().role === 'CLIENT_REPRESENTATIVE';
  }

  private readStoredUserId(): string {
    if (typeof sessionStorage === 'undefined') {
      return DEFAULT_USER_ID;
    }
    return (
      sessionStorage.getItem(USER_ID_KEY) ??
      sessionStorage.getItem(LEGACY_USER_ID_KEY) ??
      DEFAULT_USER_ID
    );
  }

  private readHeldScopes(): UserScope[] {
    if (typeof sessionStorage === 'undefined') {
      return DEFAULT_HELD_SCOPES;
    }
    const raw = sessionStorage.getItem(HELD_SCOPES_KEY);
    if (!raw) {
      return DEFAULT_HELD_SCOPES;
    }
    try {
      const parsed = JSON.parse(raw) as UserScope[];
      return Array.isArray(parsed) && parsed.length > 0 ? parsed : DEFAULT_HELD_SCOPES;
    } catch {
      return DEFAULT_HELD_SCOPES;
    }
  }

  private readActiveScope(): UserScope {
    if (typeof sessionStorage === 'undefined') {
      return DEFAULT_ACTIVE_SCOPE;
    }
    const raw = sessionStorage.getItem(ACTIVE_SCOPE_KEY);
    if (!raw) {
      return DEFAULT_ACTIVE_SCOPE;
    }
    try {
      const parsed = JSON.parse(raw) as UserScope;
      if (parsed?.legalEntityCode && parsed?.role) {
        return parsed;
      }
    } catch {
      /* fall through */
    }
    return DEFAULT_ACTIVE_SCOPE;
  }
}
