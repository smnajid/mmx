import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { userHeaders } from './user-api-headers';
import type { components } from './generated/delegated-grants';

type Schemas = components['schemas'];

export type DelegatedGrant = Schemas['DelegatedGrantResponse'];
export type CreateDelegatedGrantRequest = Schemas['CreateDelegatedGrantRequest'];
export type UpdateDelegatedGrantRequest = Schemas['UpdateDelegatedGrantRequest'];
export type GrantTenorCode = Schemas['TenorCode'];
export type GrantNoticePeriodCode = Schemas['NoticePeriodCode'];

const BASE = '/api/v1/settings/delegated-grants';

@Injectable({ providedIn: 'root' })
export class DelegatedGrantsApiService {
  private readonly http = inject(HttpClient);

  listHubGrants(userId: string): Observable<DelegatedGrant[]> {
    return this.http.get<DelegatedGrant[]>(BASE, { headers: userHeaders(userId) });
  }

  listClientGrants(userId: string): Observable<DelegatedGrant[]> {
    return this.http.get<DelegatedGrant[]>(`${BASE}/client`, { headers: userHeaders(userId) });
  }

  create(userId: string, body: CreateDelegatedGrantRequest): Observable<DelegatedGrant> {
    return this.http.post<DelegatedGrant>(BASE, body, { headers: userHeaders(userId) });
  }

  update(
    userId: string,
    hubInstitutionCode: string,
    clientLegalEntityCode: string,
    currency: string,
    body: UpdateDelegatedGrantRequest
  ): Observable<DelegatedGrant> {
    const path = `${BASE}/${encodeURIComponent(hubInstitutionCode)}/${encodeURIComponent(clientLegalEntityCode)}/${encodeURIComponent(currency)}`;
    return this.http.patch<DelegatedGrant>(path, body, { headers: userHeaders(userId) });
  }

  deactivate(
    userId: string,
    hubInstitutionCode: string,
    clientLegalEntityCode: string,
    currency: string
  ): Observable<DelegatedGrant> {
    const path = `${BASE}/${encodeURIComponent(hubInstitutionCode)}/${encodeURIComponent(clientLegalEntityCode)}/${encodeURIComponent(currency)}/deactivate`;
    return this.http.post<DelegatedGrant>(path, null, { headers: userHeaders(userId) });
  }

  reactivate(
    userId: string,
    hubInstitutionCode: string,
    clientLegalEntityCode: string,
    currency: string
  ): Observable<DelegatedGrant> {
    const path = `${BASE}/${encodeURIComponent(hubInstitutionCode)}/${encodeURIComponent(clientLegalEntityCode)}/${encodeURIComponent(currency)}/reactivate`;
    return this.http.post<DelegatedGrant>(path, null, { headers: userHeaders(userId) });
  }
}
