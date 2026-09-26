import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { userHeaders } from './user-api-headers';
import type { components } from './generated/term-rate-settings';

type Schemas = components['schemas'];

export type TenorCode = Schemas['TenorCode'];
export type TermRate = Schemas['TermRateResponse'];
export type TermRateUploadResult = Schemas['TermRateUploadResponse'];
export type TermRateTradingDay = Schemas['TermRateTradingDayResponse'];
export type TermRateRowError = Schemas['TermRateRowError'];
export type TermRateIngestError = Schemas['TermRateIngestErrorResponse'];

const BASE = '/api/v1/settings/term-rates';

@Injectable({ providedIn: 'root' })
export class TermRateSettingsApiService {
  private readonly http = inject(HttpClient);

  listForDay(traderId: string, tradingDate: string): Observable<TermRate[]> {
    const params = new HttpParams().set('tradingDate', tradingDate);
    return this.http.get<TermRate[]>(BASE, { headers: this.headers(traderId), params });
  }

  listTradingDays(traderId: string): Observable<TermRateTradingDay[]> {
    return this.http.get<TermRateTradingDay[]>(`${BASE}/days`, { headers: this.headers(traderId) });
  }

  upload(traderId: string, file: File): Observable<TermRateUploadResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<TermRateUploadResult>(`${BASE}/upload`, form, {
      headers: this.headers(traderId),
    });
  }

  downloadSample(traderId: string): Observable<Blob> {
    return this.http.get(`${BASE}/sample`, {
      headers: this.headers(traderId),
      responseType: 'blob',
    });
  }

  private headers(userId: string) {
    return userHeaders(userId);
  }
}
