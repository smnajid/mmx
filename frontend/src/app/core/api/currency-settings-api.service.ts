import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export type TenorCode = '1W' | '2W' | '1M' | '3M' | '6M' | '1Y';
export type NoticePeriodCode = '24H' | '48H';

export interface ManagedCurrency {
  code: string;
  active: boolean;
  minSubscriptionAmount: number;
  minIncreaseDecreaseAmount: number;
  enabledTenors: TenorCode[];
  enabledNoticePeriods: NoticePeriodCode[];
}

export interface OnboardCurrencyRequest {
  code: string;
  minSubscriptionAmount: number;
  minIncreaseDecreaseAmount: number;
  enabledTenors: TenorCode[];
  enabledNoticePeriods: NoticePeriodCode[];
}

export interface UpdateCurrencyRulesRequest {
  minSubscriptionAmount?: number;
  minIncreaseDecreaseAmount?: number;
  enabledTenors?: TenorCode[];
  enabledNoticePeriods?: NoticePeriodCode[];
}

const BASE = '/api/v1/settings/currencies';

@Injectable({ providedIn: 'root' })
export class CurrencySettingsApiService {
  private readonly http = inject(HttpClient);

  list(traderId: string): Observable<ManagedCurrency[]> {
    return this.http.get<ManagedCurrency[]>(BASE, { headers: this.headers(traderId) });
  }

  get(traderId: string, code: string): Observable<ManagedCurrency> {
    return this.http.get<ManagedCurrency>(`${BASE}/${code}`, { headers: this.headers(traderId) });
  }

  onboard(traderId: string, body: OnboardCurrencyRequest): Observable<ManagedCurrency> {
    return this.http.post<ManagedCurrency>(BASE, body, { headers: this.headers(traderId) });
  }

  updateRules(traderId: string, code: string, body: UpdateCurrencyRulesRequest): Observable<ManagedCurrency> {
    return this.http.patch<ManagedCurrency>(`${BASE}/${code}`, body, { headers: this.headers(traderId) });
  }

  disable(traderId: string, code: string): Observable<ManagedCurrency> {
    return this.http.post<ManagedCurrency>(`${BASE}/${code}/disable`, null, {
      headers: this.headers(traderId),
    });
  }

  enable(traderId: string, code: string): Observable<ManagedCurrency> {
    return this.http.post<ManagedCurrency>(`${BASE}/${code}/enable`, null, {
      headers: this.headers(traderId),
    });
  }

  private headers(traderId: string): HttpHeaders {
    return new HttpHeaders({ 'X-Trader-Id': traderId });
  }
}
