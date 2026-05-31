import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type TenorCode = '1W' | '2W' | '1M' | '3M' | '6M' | '1Y';

export interface TermRate {
  tradingDate: string;
  institutionCode: string;
  currency: string;
  tenor: TenorCode;
  rate: number;
  uploadedAt: string;
  uploadedBy: string;
}

export interface TermRateUploadResult {
  tradingDate: string;
  rowCount: number;
  uploadedAt: string;
}

export interface TermRateTradingDay {
  tradingDate: string;
}

export interface TermRateRowError {
  line: number;
  field?: string;
  message: string;
}

export interface TermRateIngestError {
  error: string;
  message: string;
  errors?: TermRateRowError[];
}

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

  private headers(traderId: string): HttpHeaders {
    return new HttpHeaders({ 'X-Trader-Id': traderId });
  }
}
