import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import type { components } from './generated/trader-orders-views';

type Schemas = components['schemas'];

export type OnCallNoticePeriod = Schemas['OnCallNoticePeriod'];
export type OnCallRateSegmentStatus = Schemas['OnCallRateSegmentStatus'];
export type OnCallRateSegment = Schemas['OnCallRateSegmentResponse'];
export type AddOnCallRateRequest = Schemas['AddOnCallRateRequest'];

function basePath(institutionCode: string): string {
  return `/api/v1/settings/institutions/${encodeURIComponent(institutionCode)}/oncall-rates`;
}

@Injectable({ providedIn: 'root' })
export class OnCallRateSettingsApiService {
  private readonly http = inject(HttpClient);

  list(traderId: string, institutionCode: string): Observable<OnCallRateSegment[]> {
    return this.http.get<OnCallRateSegment[]>(basePath(institutionCode), {
      headers: this.headers(traderId),
    });
  }

  add(
    traderId: string,
    institutionCode: string,
    body: AddOnCallRateRequest
  ): Observable<OnCallRateSegment> {
    return this.http.post<OnCallRateSegment>(basePath(institutionCode), body, {
      headers: this.headers(traderId),
    });
  }

  cancel(
    traderId: string,
    institutionCode: string,
    segmentId: string
  ): Observable<OnCallRateSegment> {
    return this.http.post<OnCallRateSegment>(
      `${basePath(institutionCode)}/${encodeURIComponent(segmentId)}/cancel`,
      null,
      { headers: this.headers(traderId) }
    );
  }

  private headers(traderId: string): HttpHeaders {
    return new HttpHeaders({ 'X-Trader-Id': traderId });
  }
}
