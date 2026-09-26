import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { userHeaders } from './user-api-headers';
import type { components } from './generated/institution-settings';

type Schemas = components['schemas'];

export type Institution = Schemas['InstitutionResponse'];
export type OnboardInstitutionRequest = Schemas['OnboardInstitutionRequest'];
export type GrantedInstitution = Schemas['GrantedInstitutionResponse'];
export type ClientEnablement = Schemas['ClientEnablementResponse'];
export type UpdateCounterpartyAccountsRequest = Schemas['UpdateCounterpartyAccountsRequest'];
export type UpdateClientEnablementRequest = Schemas['UpdateClientEnablementRequest'];

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

  listGrantedInstitutions(userId: string): Observable<GrantedInstitution[]> {
    return this.http.get<GrantedInstitution[]>(`${BASE}/granted`, { headers: this.headers(userId) });
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

  updateCounterpartyAccounts(
    userId: string,
    institutionCode: string,
    body: UpdateCounterpartyAccountsRequest
  ): Observable<Institution> {
    return this.http.put<Institution>(
      `${BASE}/${encodeURIComponent(institutionCode)}/counterparty-accounts`,
      body,
      { headers: this.headers(userId) }
    );
  }

  updateClientEnablement(
    userId: string,
    institutionCode: string,
    currency: string,
    body: UpdateClientEnablementRequest
  ): Observable<Institution> {
    return this.http.put<Institution>(
      `${BASE}/${encodeURIComponent(institutionCode)}/enablement/${encodeURIComponent(currency)}`,
      body,
      { headers: this.headers(userId) }
    );
  }

  private headers(userId: string) {
    return userHeaders(userId);
  }
}
