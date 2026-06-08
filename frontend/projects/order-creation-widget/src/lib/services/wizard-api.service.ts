import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type {
  ContractInfoResponse,
  CounterpartiesResponse,
  NoticePeriodsResponse,
  OnCallCurrenciesResponse,
  OperationsResponse,
  TermCurrenciesResponse,
  TenorsResponse,
} from '../models/api-responses.model';
import type { NoticePeriod, Tenor } from '../models/order-creation-payload.model';
import { WizardHostConfigService } from './wizard-host-config.service';
import { ORDER_CREATION_API_BASE_URL } from '../tokens/order-creation-api-base-url.token';

@Injectable()
export class WizardApiService {
  private readonly http = inject(HttpClient);
  private readonly injectedBaseUrl = inject(ORDER_CREATION_API_BASE_URL, { optional: true });
  private readonly hostConfig = inject(WizardHostConfigService, { optional: true });

  listTermCurrencies(): Observable<TermCurrenciesResponse> {
    return this.http.get<TermCurrenciesResponse>(
      this.url('/api/v1/order-creation/term/currencies'),
    );
  }

  listOnCallCurrencies(): Observable<OnCallCurrenciesResponse> {
    return this.http.get<OnCallCurrenciesResponse>(
      this.url('/api/v1/order-creation/oncall/currencies'),
    );
  }

  listTermOperations(currency: string): Observable<OperationsResponse> {
    return this.http.get<OperationsResponse>(
      this.url('/api/v1/order-creation/term/operations'),
      { params: new HttpParams().set('currency', currency) },
    );
  }

  listOnCallOperations(currency: string): Observable<OperationsResponse> {
    return this.http.get<OperationsResponse>(
      this.url('/api/v1/order-creation/oncall/operations'),
      { params: new HttpParams().set('currency', currency) },
    );
  }

  listTermTenors(currency: string): Observable<TenorsResponse> {
    return this.http.get<TenorsResponse>(this.url('/api/v1/order-creation/term/tenors'), {
      params: new HttpParams().set('currency', currency),
    });
  }

  listOnCallNoticePeriods(currency: string): Observable<NoticePeriodsResponse> {
    return this.http.get<NoticePeriodsResponse>(
      this.url('/api/v1/order-creation/oncall/notice-periods'),
      { params: new HttpParams().set('currency', currency) },
    );
  }

  listTermCounterparties(currency: string, tenor: Tenor): Observable<CounterpartiesResponse> {
    const params = new HttpParams().set('currency', currency).set('tenor', tenor);
    return this.http.get<CounterpartiesResponse>(
      this.url('/api/v1/order-creation/term/counterparties'),
      { params },
    );
  }

  listOnCallCounterparties(
    currency: string,
    noticePeriod: NoticePeriod,
    valueDate: string,
  ): Observable<CounterpartiesResponse> {
    const params = new HttpParams()
      .set('currency', currency)
      .set('noticePeriod', noticePeriod)
      .set('valueDate', valueDate);
    return this.http.get<CounterpartiesResponse>(
      this.url('/api/v1/order-creation/oncall/counterparties'),
      { params },
    );
  }

  getOnCallContractInfo(contractNumber: string): Observable<ContractInfoResponse> {
    return this.http.get<ContractInfoResponse>(
      this.url('/api/v1/order-creation/oncall/contract-info'),
      { params: new HttpParams().set('contractNumber', contractNumber) },
    );
  }

  private url(path: string): string {
    const apiBaseUrl = this.injectedBaseUrl ?? this.hostConfig?.apiBaseUrl ?? '';
    return `${apiBaseUrl}${path}`;
  }
}
