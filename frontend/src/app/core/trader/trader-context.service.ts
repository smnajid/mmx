import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'mmx-trader-id';

/**
 * Trader identity for X-Trader-Id on Trader-facing API calls.
 * Persisted in sessionStorage so refreshes keep the same demo identity.
 */
@Injectable({ providedIn: 'root' })
export class TraderContextService {
  readonly traderId = signal<string>(this.readStored());

  setTraderId(id: string): void {
    const trimmed = id.trim();
    if (trimmed.length === 0) {
      return;
    }
    sessionStorage.setItem(STORAGE_KEY, trimmed);
    this.traderId.set(trimmed);
  }

  private readStored(): string {
    if (typeof sessionStorage === 'undefined') {
      return 'demo-trader';
    }
    return sessionStorage.getItem(STORAGE_KEY) ?? 'demo-trader';
  }
}
