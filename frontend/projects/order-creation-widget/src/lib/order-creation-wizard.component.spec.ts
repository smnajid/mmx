import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import type { OrderCreationPayload } from './models/order-creation-payload.model';
import { WizardStepId } from './models/wizard-state.model';
import { WizardHostConfigService } from './services/wizard-host-config.service';
import { WizardStateService } from './services/wizard-state.service';
import { OrderCreationWizardComponent } from './order-creation-wizard.component';

describe('OrderCreationWizardComponent', () => {
  const apiBaseUrl = 'http://localhost:8080';
  let fixture: ComponentFixture<OrderCreationWizardComponent>;
  let http: HttpTestingController;
  let state: WizardStateService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderCreationWizardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  function flushTermCurrencyRequest(
    componentFixture: ComponentFixture<OrderCreationWizardComponent>,
    baseUrl = apiBaseUrl,
  ): void {
    const req = http.expectOne(`${baseUrl}/api/v1/order-creation/term/currencies`);
    req.flush({ tradingDate: '2026-06-07', currencies: ['EUR'] });
    componentFixture.detectChanges();
  }

  function createWizard(
    inputs: {
      apiBaseUrl?: string;
      portfolioNumber?: string;
      legalEntityCode?: string;
      orderType?: 'TERM' | 'ON_CALL';
      contractNumber?: string;
    } = {},
    options: { flushCurrency?: boolean } = {},
  ): ComponentFixture<OrderCreationWizardComponent> {
    const componentFixture = TestBed.createComponent(OrderCreationWizardComponent);
    if (inputs.apiBaseUrl !== undefined) {
      componentFixture.componentRef.setInput('apiBaseUrl', inputs.apiBaseUrl);
    }
    if (inputs.portfolioNumber !== undefined) {
      componentFixture.componentRef.setInput('portfolioNumber', inputs.portfolioNumber);
    }
    if (inputs.legalEntityCode !== undefined) {
      componentFixture.componentRef.setInput('legalEntityCode', inputs.legalEntityCode);
    } else if (inputs.portfolioNumber) {
      componentFixture.componentRef.setInput('legalEntityCode', 'LOC');
    }
    if (inputs.orderType !== undefined) {
      componentFixture.componentRef.setInput('orderType', inputs.orderType);
    }
    if (inputs.contractNumber !== undefined) {
      componentFixture.componentRef.setInput('contractNumber', inputs.contractNumber);
    }
    componentFixture.detectChanges();
    state = componentFixture.debugElement.injector.get(WizardStateService);

    if (
      options.flushCurrency !== false &&
      inputs.orderType === 'TERM' &&
      !inputs.contractNumber &&
      inputs.portfolioNumber
    ) {
      flushTermCurrencyRequest(componentFixture, inputs.apiBaseUrl ?? '');
    }

    return componentFixture;
  }

  afterEach(() => {
    http.match(() => true).forEach((req) => req.flush({}));
    http.verify();
    TestBed.resetTestingModule();
  });

  it('shows configuration error when portfolioNumber is missing', () => {
    fixture = createWizard({ apiBaseUrl, portfolioNumber: '', legalEntityCode: 'LOC' });
    expect(fixture.nativeElement.querySelector('[data-testid="wizard-config-error"]')?.textContent).toContain(
      'portfolioNumber is required',
    );
  });

  it('shows configuration error when legalEntityCode is missing', () => {
    fixture = createWizard({ apiBaseUrl, portfolioNumber: 'PF-001', legalEntityCode: '' });
    expect(fixture.nativeElement.querySelector('[data-testid="wizard-config-error"]')?.textContent).toContain(
      'legalEntityCode is required',
    );
  });

  it('allows empty apiBaseUrl for same-origin relative API paths', () => {
    fixture = createWizard({ apiBaseUrl: '', portfolioNumber: 'PF-001', orderType: 'TERM' });

    expect(fixture.nativeElement.querySelector('[data-testid="wizard-config-error"]')).toBeFalsy();
    expect(state.state().currentStep).toBe(WizardStepId.CURRENCY);
  });

  it('starts at currency step when orderType is pre-selected', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      orderType: 'TERM',
    });

    expect(state.state().orderType).toBe('TERM');
    expect(state.state().currentStep).toBe(WizardStepId.CURRENCY);
    expect(fixture.nativeElement.querySelector('mmx-step-currency')).toBeTruthy();
  });

  it('seeds locked institution from contract-info response', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      contractNumber: 'CT-00042',
    });

    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/contract-info?contractNumber=CT-00042`)
      .flush({
        currency: 'EUR',
        noticePeriod: '24H',
        institutionCode: 'BNKCO',
        counterparty: 'BankCo',
      });
    fixture.detectChanges();
    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/operations?currency=EUR`)
      .flush({ operations: [{ operation: 'INCREASE', minAmount: 50000 }] });
    fixture.detectChanges();

    expect(state.state().contractInstitutionCode).toBe('BNKCO');
    expect(state.state().contractCounterparty).toBe('BankCo');
  });

  it('resolves contractNumber shortcut via contract-info API', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      contractNumber: 'CT-00042',
    });

    const req = http.expectOne(
      `${apiBaseUrl}/api/v1/order-creation/oncall/contract-info?contractNumber=CT-00042`,
    );
    req.flush({
      currency: 'EUR',
      noticePeriod: '24H',
      institutionCode: 'BNKCO',
      counterparty: 'BankCo',
    });
    fixture.detectChanges();
    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/operations?currency=EUR`)
      .flush({ operations: [{ operation: 'INCREASE', minAmount: 50000 }] });
    fixture.detectChanges();

    expect(state.state()).toMatchObject({
      contractShortcut: true,
      orderType: 'ON_CALL',
      currency: 'EUR',
      noticePeriod: '24H',
      sourceContractNumber: 'CT-00042',
      contractInstitutionCode: 'BNKCO',
      contractCounterparty: 'BankCo',
      currentStep: WizardStepId.OPERATION,
    });
    expect(fixture.nativeElement.querySelector('mmx-step-operation')).toBeTruthy();
  });

  it('shows contract not found error with start-fresh fallback on 404', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      contractNumber: 'CT-99999',
    });

    http
      .expectOne(
        `${apiBaseUrl}/api/v1/order-creation/oncall/contract-info?contractNumber=CT-99999`,
      )
      .flush('Not found', { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="wizard-contract-error"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Contract not found');

    fixture.nativeElement.querySelector('[data-testid="wizard-start-fresh"]').click();
    fixture.detectChanges();

    expect(state.state().currentStep).toBe(WizardStepId.ORDER_TYPE);
    expect(state.state().contractShortcut).toBe(false);
    expect(fixture.nativeElement.querySelector('mmx-wizard-shell')).toBeTruthy();
  });

  it('emits orderReady with portfolioNumber from review', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      orderType: 'TERM',
    });

    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('SUBSCRIPTION', 500000);
    state.completeAndAdvance();
    state.setTenor('3M');
    state.completeAndAdvance();
    state.setCounterparty('BNKCO', 'BankCo', 3.5, '2026-06-07');
    state.completeAndAdvance();
    state.setOrderDetails(1000000, '2026-06-10');
    state.completeAndAdvance();
    fixture.detectChanges();

    const payloads: OrderCreationPayload[] = [];
    fixture.componentInstance.orderReady.subscribe((payload) => payloads.push(payload));
    fixture.nativeElement.querySelector('[data-testid="review-create-order"]').click();
    fixture.detectChanges();

    expect(payloads[0]?.portfolioNumber).toBe('PF-001');
    expect(payloads[0]?.currency).toBe('EUR');
  });

  it('emits DECREASE shortcut payload with institution but no rate dependency', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      contractNumber: 'CT-00042',
    });

    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/contract-info?contractNumber=CT-00042`)
      .flush({
        currency: 'EUR',
        noticePeriod: '24H',
        institutionCode: 'BNKCO',
        counterparty: 'BankCo',
      });
    fixture.detectChanges();
    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/operations?currency=EUR`)
      .flush({ operations: [{ operation: 'DECREASE', minAmount: 50000 }] });
    fixture.detectChanges();

    state.setOperation('DECREASE', 50000);
    state.completeAndAdvance();
    state.setValueDate('2026-06-10');
    state.completeAndAdvance();
    state.setCounterparty('BNKCO', 'BankCo');
    state.completeAndAdvance();
    state.setOrderDetails(600000);
    state.completeAndAdvance();
    fixture.detectChanges();

    const payloads: OrderCreationPayload[] = [];
    fixture.componentInstance.orderReady.subscribe((payload) => payloads.push(payload));
    fixture.nativeElement.querySelector('[data-testid="review-create-order"]').click();
    fixture.detectChanges();

    expect(payloads[0]?.operation).toBe('DECREASE');
    expect(payloads[0]?.institutionCode).toBe('BNKCO');
    expect(payloads[0]?.counterparty).toBe('BankCo');
    expect(payloads[0]?.sourceContractNumber).toBe('CT-00042');
  });

  it('emits shortcut-derived sourceContractNumber from a lifecycle flow', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      contractNumber: 'CT-00042',
    });

    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/contract-info?contractNumber=CT-00042`)
      .flush({ currency: 'EUR', noticePeriod: '24H' });
    fixture.detectChanges();
    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/operations?currency=EUR`)
      .flush({ operations: [{ operation: 'INCREASE', minAmount: 50000 }] });
    fixture.detectChanges();

    state.setOperation('INCREASE', 50000);
    state.completeAndAdvance();
    state.setValueDate('2026-06-10');
    state.completeAndAdvance();
    state.setCounterparty('BNKCO', 'BankCo', 3.5, '2026-06-07');
    state.completeAndAdvance();
    state.setOrderDetails(600000);
    state.completeAndAdvance();
    fixture.detectChanges();

    const payloads: OrderCreationPayload[] = [];
    fixture.componentInstance.orderReady.subscribe((payload) => payloads.push(payload));
    fixture.nativeElement.querySelector('[data-testid="review-create-order"]').click();
    fixture.detectChanges();

    expect(payloads[0]?.operation).toBe('INCREASE');
    expect(payloads[0]?.sourceContractNumber).toBe('CT-00042');
    expect(payloads[0]?.valueDate).toBe('2026-06-10');
  });

  it('OnCall full flow visible steps include VALUE_DATE before counterparty', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      orderType: 'ON_CALL',
    });

    const steps = state.visibleSteps();
    const valueDateIndex = steps.indexOf(WizardStepId.VALUE_DATE);
    const counterpartyIndex = steps.indexOf(WizardStepId.COUNTERPARTY);
    const noticeIndex = steps.indexOf(WizardStepId.TENOR_OR_NOTICE_PERIOD);

    expect(valueDateIndex).toBeGreaterThan(noticeIndex);
    expect(counterpartyIndex).toBeGreaterThan(valueDateIndex);
  });

  it('emits OnCall orderReady with single valueDate from value-date step', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      orderType: 'ON_CALL',
    });

    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('INCREASE', 500000);
    state.completeAndAdvance();
    state.setNoticePeriod('48H');
    state.completeAndAdvance();
    state.setValueDate('2026-06-30');
    state.completeAndAdvance();
    state.setCounterparty('BNKCO', 'BankCo', 3.5, '2026-06-07');
    state.completeAndAdvance();
    state.setOrderDetails(600000);
    state.completeAndAdvance();
    fixture.detectChanges();

    const payloads: OrderCreationPayload[] = [];
    fixture.componentInstance.orderReady.subscribe((payload) => payloads.push(payload));
    fixture.nativeElement.querySelector('[data-testid="review-create-order"]').click();
    fixture.detectChanges();

    expect(payloads[0]?.valueDate).toBe('2026-06-30');
    expect(payloads[0]?.noticePeriod).toBe('48H');
  });

  it('emits cancelled from review', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      orderType: 'TERM',
    });

    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('SUBSCRIPTION', 500000);
    state.completeAndAdvance();
    state.setTenor('3M');
    state.completeAndAdvance();
    state.setCounterparty('BNKCO', 'BankCo', 3.5, '2026-06-07');
    state.completeAndAdvance();
    state.setOrderDetails(1000000, '2026-06-10');
    state.completeAndAdvance();
    fixture.detectChanges();

    const cancelled: boolean[] = [];
    fixture.componentInstance.cancelled.subscribe(() => cancelled.push(true));
    fixture.nativeElement.querySelector('[data-testid="review-cancel"]').click();
    fixture.detectChanges();

    expect(cancelled).toEqual([true]);
  });

  it('provides api base URL to WizardApiService via host config', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      orderType: 'TERM',
    });

    const hostConfig = fixture.debugElement.injector.get(WizardHostConfigService);
    expect(hostConfig.apiBaseUrl).toBe(apiBaseUrl);
  });

  it('syncs hostConfig.legalEntityCode when legalEntityCode input changes', () => {
    fixture = createWizard({
      apiBaseUrl,
      portfolioNumber: 'PF-001',
      orderType: 'TERM',
      legalEntityCode: 'LOC',
    });

    const hostConfig = fixture.debugElement.injector.get(WizardHostConfigService);
    expect(hostConfig.legalEntityCode).toBe('LOC');

    fixture.componentRef.setInput('legalEntityCode', 'PAR');
    fixture.detectChanges();

    expect(hostConfig.legalEntityCode).toBe('PAR');
  });
});
