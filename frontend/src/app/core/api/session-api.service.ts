import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type { components } from './generated/trader-orders-views';
import { userHeaders } from './user-api-headers';

type Schemas = components['schemas'];

export type UserRole = Schemas['UserRole'];
export type ReScopeRequest = Schemas['ReScopeRequest'];
export type ReScopeResponse = Schemas['ReScopeResponse'];

const SCOPE_URL = '/api/v1/session/scope';

@Injectable({ providedIn: 'root' })
export class SessionApiService {
  private readonly http = inject(HttpClient);

  reScope(userId: string, request: ReScopeRequest): Observable<ReScopeResponse> {
    return this.http.post<ReScopeResponse>(SCOPE_URL, request, {
      headers: userHeaders(userId),
    });
  }
}
