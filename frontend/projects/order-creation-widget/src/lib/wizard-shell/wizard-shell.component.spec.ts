import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { WizardStepId } from '../models/wizard-state.model';
import { WizardApiService } from '../services/wizard-api.service';
import { WizardStateService } from '../services/wizard-state.service';
import { ORDER_CREATION_API_BASE_URL } from '../tokens/order-creation-api-base-url.token';
import { WizardShellComponent } from './wizard-shell.component';

describe('WizardShellComponent', () => {
  const apiBaseUrl = 'http://localhost:8080';
  let fixture: ComponentFixture<WizardShellComponent>;
  let http: HttpTestingController;
  let state: WizardStateService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WizardShellComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ORDER_CREATION_API_BASE_URL, useValue: apiBaseUrl },
        WizardApiService,
        WizardStateService,
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    state = TestBed.inject(WizardStateService);
    fixture = TestBed.createComponent(WizardShellComponent);
    fixture.componentRef.setInput('portfolioNumber', 'PF-001');
    fixture.detectChanges();
  });

  afterEach(() => {
    http.match(() => true).forEach((req) => req.flush({}));
    http.verify();
    TestBed.resetTestingModule();
  });

  function flushCurrencyRequest(): void {
    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/term/currencies`)
      .flush({ tradingDate: '2026-06-07', currencies: ['EUR'] });
    fixture.detectChanges();
  }

  function flushOperationRequest(): void {
    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/term/operations?currency=EUR`)
      .flush({ operations: [{ operation: 'SUBSCRIPTION', minAmount: 100000 }] });
    fixture.detectChanges();
  }

  it('renders the correct number of step indicators for the full flow', () => {
    expect(fixture.nativeElement.querySelectorAll('[data-testid^="step-indicator-"]').length).toBe(7);
  });

  it('renders fewer steps when order type is pre-selected', () => {
    state.initialize({ orderType: 'TERM', skipOrderType: true });
    fixture.detectChanges();
    flushCurrencyRequest();

    expect(fixture.nativeElement.querySelectorAll('[data-testid^="step-indicator-"]').length).toBe(6);
    expect(
      fixture.nativeElement.querySelector('[data-testid="step-indicator-ORDER_TYPE"]'),
    ).toBeFalsy();
  });

  it('supports back and next navigation between completed steps', () => {
    state.setOrderType('TERM');
    state.completeAndAdvance();
    fixture.detectChanges();
    flushCurrencyRequest();

    expect(state.state().currentStep).toBe(WizardStepId.CURRENCY);

    fixture.nativeElement.querySelector('[data-testid="wizard-back"]').click();
    fixture.detectChanges();
    expect(state.state().currentStep).toBe(WizardStepId.ORDER_TYPE);

    fixture.nativeElement.querySelector('[data-testid="wizard-next"]').click();
    fixture.detectChanges();
    flushCurrencyRequest();
    expect(state.state().currentStep).toBe(WizardStepId.CURRENCY);
  });

  it('allows clicking a completed step in the indicator', () => {
    state.setOrderType('TERM');
    state.completeAndAdvance();
    fixture.detectChanges();
    flushCurrencyRequest();
    state.setCurrency('EUR');
    state.completeAndAdvance();
    fixture.detectChanges();
    flushOperationRequest();

    expect(state.state().currentStep).toBe(WizardStepId.OPERATION);

    fixture.nativeElement.querySelector('[data-testid="step-indicator-ORDER_TYPE"]').click();
    fixture.detectChanges();
    expect(state.state().currentStep).toBe(WizardStepId.ORDER_TYPE);
  });

  it('disables forward navigation when the current step is incomplete', () => {
    const nextButton: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="wizard-next"]');
    expect(nextButton.disabled).toBe(true);

    state.setOrderType('TERM');
    fixture.detectChanges();
    expect(nextButton.disabled).toBe(false);
  });
});
