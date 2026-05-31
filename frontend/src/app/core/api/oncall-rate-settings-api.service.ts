import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export type OnCallNoticePeriod = '24H' | '48H';

export type OnCallRateSegmentStatus = 'PENDING_CONFIRMATION' | 'VALID' | 'CANCELED';

export interface OnCallRateSegment {
  segmentId: string;
  institutionCode: string;
  currency: string;
  noticePeriod: OnCallNoticePeriod;
  rate: number;
  valueDate: string;
  endDate: string;
  status: OnCallRateSegmentStatus;
  validatedAt?: string;
}

export interface AddOnCallRateRequest {
  currency: string;
  noticePeriod: OnCallNoticePeriod;
  rate: number;
  valueDate: string;
}

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
