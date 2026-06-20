import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import type { components } from './generated/institution-settings';

type Schemas = components['schemas'];

export type Institution = Schemas['InstitutionResponse'];
export type OnboardInstitutionRequest = Schemas['OnboardInstitutionRequest'];

const BASE = '/api/v1/settings/institutions';

@Injectable({ providedIn: 'root' })
export class InstitutionSettingsApiService {
  private readonly http = inject(HttpClient);

  list(traderId: string, activeOnly = false): Observable<Institution[]> {
    let params = new HttpParams();
    if (activeOnly) {
      params = params.set('activeOnly', 'true');
    }
    return this.http.get<Institution[]>(BASE, { headers: this.headers(traderId), params });
  }

  get(traderId: string, institutionCode: string): Observable<Institution> {
    return this.http.get<Institution>(`${BASE}/${encodeURIComponent(institutionCode)}`, {
      headers: this.headers(traderId),
    });
  }

  onboard(traderId: string, body: OnboardInstitutionRequest): Observable<Institution> {
    return this.http.post<Institution>(BASE, body, { headers: this.headers(traderId) });
  }

  deactivate(traderId: string, institutionCode: string): Observable<Institution> {
    return this.http.post<Institution>(
      `${BASE}/${encodeURIComponent(institutionCode)}/deactivate`,
      null,
      { headers: this.headers(traderId) }
    );
  }

  activate(traderId: string, institutionCode: string): Observable<Institution> {
    return this.http.post<Institution>(
      `${BASE}/${encodeURIComponent(institutionCode)}/activate`,
      null,
      { headers: this.headers(traderId) }
    );
  }

  private headers(traderId: string): HttpHeaders {
    return new HttpHeaders({ 'X-Trader-Id': traderId });
  }
}
