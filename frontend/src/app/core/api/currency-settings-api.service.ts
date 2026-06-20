import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import type { components } from './generated/currency-settings';

type Schemas = components['schemas'];

export type TenorCode = Schemas['TenorCode'];
export type NoticePeriodCode = Schemas['NoticePeriodCode'];
export type ManagedCurrency = Schemas['ManagedCurrencyResponse'];
export type OnboardCurrencyRequest = Schemas['OnboardCurrencyRequest'];
export type UpdateCurrencyRulesRequest = Schemas['UpdateCurrencyRulesRequest'];

const BASE = '/api/v1/settings/currencies';

@Injectable({ providedIn: 'root' })
export class CurrencySettingsApiService {
  private readonly http = inject(HttpClient);

  list(traderId: string): Observable<ManagedCurrency[]> {
    return this.http.get<ManagedCurrency[]>(BASE, { headers: this.headers(traderId) });
  }

  get(traderId: string, code: string): Observable<ManagedCurrency> {
    return this.http.get<ManagedCurrency>(`${BASE}/${encodeURIComponent(code)}`, {
      headers: this.headers(traderId),
    });
  }

  onboard(traderId: string, body: OnboardCurrencyRequest): Observable<ManagedCurrency> {
    return this.http.post<ManagedCurrency>(BASE, body, { headers: this.headers(traderId) });
  }

  updateRules(
    traderId: string,
    code: string,
    body: UpdateCurrencyRulesRequest
  ): Observable<ManagedCurrency> {
    return this.http.patch<ManagedCurrency>(`${BASE}/${encodeURIComponent(code)}`, body, {
      headers: this.headers(traderId),
    });
  }

  disable(traderId: string, code: string): Observable<ManagedCurrency> {
    return this.http.post<ManagedCurrency>(
      `${BASE}/${encodeURIComponent(code)}/disable`,
      null,
      { headers: this.headers(traderId) }
    );
  }

  enable(traderId: string, code: string): Observable<ManagedCurrency> {
    return this.http.post<ManagedCurrency>(
      `${BASE}/${encodeURIComponent(code)}/enable`,
      null,
      { headers: this.headers(traderId) }
    );
  }

  private headers(traderId: string): HttpHeaders {
    return new HttpHeaders({ 'X-Trader-Id': traderId });
  }
}
