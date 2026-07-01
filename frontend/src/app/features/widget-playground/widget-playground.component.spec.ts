import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { OrderCreationWizardComponent } from 'order-creation-widget';
import { vi } from 'vitest';
import { WidgetPlaygroundComponent } from './widget-playground.component';

describe('WidgetPlaygroundComponent', () => {
  let fixture: ComponentFixture<WidgetPlaygroundComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WidgetPlaygroundComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WidgetPlaygroundComponent);
    vi.spyOn(fixture.componentInstance, 'ngOnInit').mockImplementation(() => {});
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  const onCallContract = {
    contractNumber: 'CT-00042',
    orderType: 'ON_CALL' as const,
    currency: 'EUR',
    noticePeriod: '24H',
    valueDate: '2026-06-01',
    originalAmount: 5000000,
  };

  function applyOnCallConfig(): void {
    fixture.componentInstance.orderType = 'ON_CALL';
    fixture.componentInstance.portfolioNumber = 'PF-001';
    fixture.componentInstance.apiBaseUrl = 'http://localhost:8080';
    fixture.componentInstance.applyConfig();
    fixture.detectChanges();
  }

  it('selecting an OnCall contract mounts the widget with contractNumber set', async () => {
    applyOnCallConfig();

    httpMock
      .expectOne(
        'http://localhost:8080/api/v1/order-creation/contracts?portfolioNumber=PF-001&orderType=ON_CALL',
      )
      .flush({ contracts: [onCallContract] });
    await fixture.whenStable();
    fixture.detectChanges();

    fixture.componentInstance.selectContract(onCallContract);
    await fixture.whenStable();
    fixture.detectChanges();
    await new Promise<void>((resolve) => queueMicrotask(() => resolve()));
    fixture.detectChanges();
    await fixture.whenStable();

    const wizard = fixture.debugElement.query(By.directive(OrderCreationWizardComponent));
    expect(wizard).toBeTruthy();
    expect(wizard.componentInstance.contractNumber()).toBe('CT-00042');

    httpMock
      .expectOne(
        'http://localhost:8080/api/v1/order-creation/oncall/contract-info?contractNumber=CT-00042',
      )
      .flush({
        currency: 'EUR',
        noticePeriod: '24H',
        institutionCode: 'BNKCO',
        counterparty: 'BankCo',
      });
    await fixture.whenStable();
    fixture.detectChanges();

    httpMock
      .expectOne('http://localhost:8080/api/v1/order-creation/oncall/operations?currency=EUR')
      .flush({ operations: [{ operation: 'INCREASE', minAmount: 50000 }] });
    await fixture.whenStable();
  });

  it('shows OnCall lifecycle hint when config is applied but no contract is selected', async () => {
    applyOnCallConfig();

    httpMock
      .expectOne(
        'http://localhost:8080/api/v1/order-creation/contracts?portfolioNumber=PF-001&orderType=ON_CALL',
      )
      .flush({ contracts: [onCallContract] });
    await fixture.whenStable();
    fixture.detectChanges();

    const placeholder = fixture.nativeElement.querySelector(
      '[data-testid="playground-widget-placeholder"]',
    );
    expect(placeholder?.textContent).toContain('Select a live contract from the picker');
    expect(placeholder?.textContent).toContain('Full flow');
    expect(fixture.nativeElement.querySelector('mmx-order-creation-wizard')).toBeFalsy();
  });

  it('shows seed hint when OnCall has no live contracts', async () => {
    applyOnCallConfig();

    httpMock
      .expectOne(
        'http://localhost:8080/api/v1/order-creation/contracts?portfolioNumber=PF-001&orderType=ON_CALL',
      )
      .flush({ contracts: [] });
    await fixture.whenStable();
    fixture.detectChanges();

    const placeholder = fixture.nativeElement.querySelector(
      '[data-testid="playground-widget-placeholder"]',
    );
    expect(placeholder?.textContent).toContain('No live contracts');

    const pickerEmpty = fixture.nativeElement.querySelector(
      '[data-testid="playground-contract-picker-empty"]',
    );
    expect(pickerEmpty?.textContent).toContain('seed-demo-orders.sh');
    expect(pickerEmpty?.textContent).toContain('Full flow');
  });

  const sampleOrderReadyPayload = {
    legalEntityCode: 'LOC',
    portfolioNumber: 'PF-001',
    orderType: 'TERM' as const,
    currency: 'EUR',
    operation: 'SUBSCRIPTION' as const,
    tenor: '3M' as const,
    institutionCode: 'BNKCO',
    counterparty: 'BankCo',
    amount: 1_000_000,
    valueDate: '2026-06-15',
  };

  function logOrderReady(): void {
    fixture.componentInstance.activeApiBaseUrl.set('http://localhost:8080');
    fixture.componentInstance.onOrderReady(sampleOrderReadyPayload);
    fixture.detectChanges();
  }

  it('sends orderReady payload to intake with mapped body', async () => {
    logOrderReady();

    fixture.nativeElement.querySelector('[data-testid="playground-send-order"]').click();
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/v1/orders');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.externalOrderReference).toMatch(/^PLAYGROUND-/);
    expect(req.request.body).toMatchObject({
      legalEntityCode: 'LOC',
      orderType: 'TERM',
      orderOperation: 'SUBSCRIPTION',
      portfolioNumber: 'PF-001',
      currency: 'EUR',
      amount: 1_000_000,
      valueDate: '2026-06-15',
      institutionCode: 'BNKCO',
      tenor: '3M',
    });
    expect(req.request.body).not.toHaveProperty('counterparty');

    req.flush({ orderId: 'order-abc', status: 'RECEIVED' }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    fixture.detectChanges();

    const success = fixture.nativeElement.querySelector('[data-testid="playground-submit-success"]');
    expect(success?.textContent).toContain('order-abc');
    expect(success?.textContent).toContain('201');
    expect(fixture.nativeElement.querySelector('[data-testid="playground-send-order"]')).toBeFalsy();
  });

  it('surfaces intake errors and allows retry send', async () => {
    logOrderReady();

    fixture.nativeElement.querySelector('[data-testid="playground-send-order"]').click();
    fixture.detectChanges();

    const firstReq = httpMock.expectOne('http://localhost:8080/api/v1/orders');
    const firstRef = firstReq.request.body.externalOrderReference;
    firstReq.flush(
      { error: 'VALIDATION_ERROR', message: 'valueDate is too soon' },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="playground-submit-error"]')?.textContent)
      .toContain('valueDate is too soon');

    fixture.nativeElement.querySelector('[data-testid="playground-send-order"]').click();
    fixture.detectChanges();

    const retryReq = httpMock.expectOne('http://localhost:8080/api/v1/orders');
    expect(retryReq.request.body.externalOrderReference).toMatch(/^PLAYGROUND-/);
    expect(retryReq.request.body.externalOrderReference).not.toBe(firstRef);
    retryReq.flush({ orderId: 'order-retry', status: 'RECEIVED' }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="playground-submit-success"]')?.textContent)
      .toContain('order-retry');
  });

  it('lists Term contracts as non-actionable', async () => {
    fixture.componentInstance.orderType = 'TERM';
    fixture.componentInstance.portfolioNumber = 'PF-001';
    fixture.componentInstance.applyConfig();
    fixture.detectChanges();

    httpMock
      .expectOne((req) => req.url.includes('/api/v1/order-creation/contracts'))
      .flush({
        contracts: [
          {
            contractNumber: 'CT-00100',
            orderType: 'TERM',
            currency: 'EUR',
            tenor: '3M',
            valueDate: '2026-06-01',
            endDate: '2026-09-01',
            originalAmount: 10000000,
          },
        ],
      });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="playground-contract-option"]'),
    ).toBeFalsy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="playground-contract-option-term"]'),
    ).toBeTruthy();

    httpMock
      .expectOne((req) => req.url.includes('/api/v1/order-creation/term/currencies'))
      .flush({ tradingDate: '2026-06-07', currencies: ['EUR'] });
    await fixture.whenStable();
  });
});
