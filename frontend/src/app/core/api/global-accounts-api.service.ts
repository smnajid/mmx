import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { userHeaders } from './user-api-headers';

export interface GlobalAccount {
  clientLegalEntityCode: string;
  hubLegalEntityCode: string;
  currency: string;
  accountRef: string;
}

export interface UpsertGlobalAccountRequest {
  clientLegalEntityCode: string;
  currency: string;
  accountRef: string;
}

const BASE = '/api/v1/settings/global-accounts';

@Injectable({ providedIn: 'root' })
export class GlobalAccountsApiService {
  private readonly http = inject(HttpClient);

  list(userId: string): Observable<GlobalAccount[]> {
    return this.http.get<GlobalAccount[]>(BASE, { headers: userHeaders(userId) });
  }

  upsert(userId: string, body: UpsertGlobalAccountRequest): Observable<GlobalAccount> {
    return this.http.put<GlobalAccount>(BASE, body, { headers: userHeaders(userId) });
  }
}
