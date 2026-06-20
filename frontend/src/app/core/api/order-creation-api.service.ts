import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface CounterpartyRow {
  institutionCode: string;
  displayName: string;
  rate: number;
  rateDate: string;
  indicative: boolean;
}

export interface CounterpartiesResponse {
  counterparties: CounterpartyRow[];
}

@Injectable({ providedIn: 'root' })
export class OrderCreationApiService {
  private readonly http = inject(HttpClient);

  listTermCounterparties(
    currency: string,
    tenor: string,
  ): Observable<CounterpartiesResponse> {
    const params = new HttpParams().set('currency', currency).set('tenor', tenor);
    return this.http.get<CounterpartiesResponse>(
      '/api/v1/order-creation/term/counterparties',
      { params },
    );
  }

  listOnCallCounterparties(
    currency: string,
    noticePeriod: string,
    valueDate: string,
  ): Observable<CounterpartiesResponse> {
    const params = new HttpParams()
      .set('currency', currency)
      .set('noticePeriod', noticePeriod)
      .set('valueDate', valueDate);
    return this.http.get<CounterpartiesResponse>(
      '/api/v1/order-creation/oncall/counterparties',
      { params },
    );
  }
}
