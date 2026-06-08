import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { ORDER_CREATION_API_BASE_URL } from '../tokens/order-creation-api-base-url.token';
import { WizardApiService } from './wizard-api.service';

describe('WizardApiService', () => {
  const apiBaseUrl = 'http://localhost:8080';
  let service: WizardApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ORDER_CREATION_API_BASE_URL, useValue: apiBaseUrl },
        WizardApiService,
      ],
    });

    service = TestBed.inject(WizardApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('listTermCurrencies calls GET /api/v1/order-creation/term/currencies', async () => {
    const promise = firstValueFrom(service.listTermCurrencies());
    const req = http.expectOne(`${apiBaseUrl}/api/v1/order-creation/term/currencies`);
    expect(req.request.method).toBe('GET');
    req.flush({ tradingDate: '2026-06-07', currencies: ['EUR', 'USD'] });
    await expect(promise).resolves.toEqual({
      tradingDate: '2026-06-07',
      currencies: ['EUR', 'USD'],
    });
  });

  it('listOnCallCurrencies calls GET /api/v1/order-creation/oncall/currencies', async () => {
    const promise = firstValueFrom(service.listOnCallCurrencies());
    const req = http.expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/currencies`);
    expect(req.request.method).toBe('GET');
    req.flush({ currencies: ['EUR'] });
    await expect(promise).resolves.toEqual({ currencies: ['EUR'] });
  });

  it('listTermOperations calls GET with currency query param', async () => {
    const promise = firstValueFrom(service.listTermOperations('EUR'));
    const req = http.expectOne(
      `${apiBaseUrl}/api/v1/order-creation/term/operations?currency=EUR`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ operations: [{ operation: 'SUBSCRIPTION', minAmount: 100000 }] });
    await expect(promise).resolves.toEqual({
      operations: [{ operation: 'SUBSCRIPTION', minAmount: 100000 }],
    });
  });

  it('listOnCallOperations calls GET with currency query param', async () => {
    const promise = firstValueFrom(service.listOnCallOperations('USD'));
    const req = http.expectOne(
      `${apiBaseUrl}/api/v1/order-creation/oncall/operations?currency=USD`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      operations: [
        { operation: 'SUBSCRIPTION', minAmount: 50000 },
        { operation: 'INCREASE', minAmount: 10000 },
      ],
    });
    await expect(promise).resolves.toEqual({
      operations: [
        { operation: 'SUBSCRIPTION', minAmount: 50000 },
        { operation: 'INCREASE', minAmount: 10000 },
      ],
    });
  });

  it('listTermTenors calls GET with currency query param', async () => {
    const promise = firstValueFrom(service.listTermTenors('EUR'));
    const req = http.expectOne(
      `${apiBaseUrl}/api/v1/order-creation/term/tenors?currency=EUR`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ tenors: ['1M', '3M'] });
    await expect(promise).resolves.toEqual({ tenors: ['1M', '3M'] });
  });

  it('listOnCallNoticePeriods calls GET with currency query param', async () => {
    const promise = firstValueFrom(service.listOnCallNoticePeriods('EUR'));
    const req = http.expectOne(
      `${apiBaseUrl}/api/v1/order-creation/oncall/notice-periods?currency=EUR`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ noticePeriods: ['24H', '48H'] });
    await expect(promise).resolves.toEqual({ noticePeriods: ['24H', '48H'] });
  });

  it('listTermCounterparties calls GET with currency and tenor query params', async () => {
    const promise = firstValueFrom(service.listTermCounterparties('EUR', '3M'));
    const req = http.expectOne(
      `${apiBaseUrl}/api/v1/order-creation/term/counterparties?currency=EUR&tenor=3M`,
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
      service.listOnCallCounterparties('EUR', '24H', '2026-06-10'),
    );
    const req = http.expectOne(
      `${apiBaseUrl}/api/v1/order-creation/oncall/counterparties?currency=EUR&noticePeriod=24H&valueDate=2026-06-10`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ counterparties: [] });
    await expect(promise).resolves.toEqual({ counterparties: [] });
  });

  it('getOnCallContractInfo calls GET with contractNumber query param', async () => {
    const promise = firstValueFrom(service.getOnCallContractInfo('CT-00042'));
    const req = http.expectOne(
      `${apiBaseUrl}/api/v1/order-creation/oncall/contract-info?contractNumber=CT-00042`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ currency: 'EUR', noticePeriod: '24H' });
    await expect(promise).resolves.toEqual({ currency: 'EUR', noticePeriod: '24H' });
  });
});
