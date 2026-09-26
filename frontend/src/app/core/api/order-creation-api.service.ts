import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import type { components } from './generated/trader-orders-views';

type Schemas = components['schemas'];

export type CounterpartyRow = Schemas['CounterpartyOption'];
export type CounterpartiesResponse = Schemas['CounterpartiesResponse'];

@Injectable({ providedIn: 'root' })
export class OrderCreationApiService {
  private readonly http = inject(HttpClient);

  listTermCounterparties(
    legalEntityCode: string,
    currency: string,
    tenor: string,
  ): Observable<CounterpartiesResponse> {
    const params = new HttpParams()
      .set('legalEntityCode', legalEntityCode)
      .set('currency', currency)
      .set('tenor', tenor);
    return this.http.get<CounterpartiesResponse>(
      '/api/v1/order-creation/term/counterparties',
      { params },
    );
  }

  listOnCallCounterparties(
    legalEntityCode: string,
    currency: string,
    noticePeriod: string,
    valueDate: string,
  ): Observable<CounterpartiesResponse> {
    const params = new HttpParams()
      .set('legalEntityCode', legalEntityCode)
      .set('currency', currency)
      .set('noticePeriod', noticePeriod)
      .set('valueDate', valueDate);
    return this.http.get<CounterpartiesResponse>(
      '/api/v1/order-creation/oncall/counterparties',
      { params },
    );
  }
}
