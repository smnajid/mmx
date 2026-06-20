import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { OrderCreationApiService } from './order-creation-api.service';

describe('OrderCreationApiService', () => {
  let service: OrderCreationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), OrderCreationApiService],
    });

    service = TestBed.inject(OrderCreationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('listTermCounterparties calls GET with currency and tenor query params', async () => {
    const promise = firstValueFrom(service.listTermCounterparties('EUR', '3M'));
    const req = http.expectOne(
      '/api/v1/order-creation/term/counterparties?currency=EUR&tenor=3M',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      counterparties: [
        {
          institutionCode: 'BNKCO',
          displayName: 'BankCo',
          rate: 3.5,
          rateDate: '2026-06-07',
          indicative: false,
        },
      ],
    });
    await expect(promise).resolves.toEqual({
      counterparties: [
        {
          institutionCode: 'BNKCO',
          displayName: 'BankCo',
          rate: 3.5,
          rateDate: '2026-06-07',
          indicative: false,
        },
      ],
    });
  });

  it('listOnCallCounterparties calls GET with currency, noticePeriod, and valueDate', async () => {
    const promise = firstValueFrom(
      service.listOnCallCounterparties('CHF', '48H', '2026-06-30'),
    );
    const req = http.expectOne(
      '/api/v1/order-creation/oncall/counterparties?currency=CHF&noticePeriod=48H&valueDate=2026-06-30',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      counterparties: [
        {
          institutionCode: 'QNB-01',
          displayName: 'QNB',
          rate: 2.15,
          rateDate: '2026-06-20',
          indicative: true,
        },
      ],
    });
    await expect(promise).resolves.toEqual({
      counterparties: [
        {
          institutionCode: 'QNB-01',
          displayName: 'QNB',
          rate: 2.15,
          rateDate: '2026-06-20',
          indicative: true,
        },
      ],
    });
  });
});
