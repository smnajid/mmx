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
import { StepCurrencyComponent } from './step-currency.component';

describe('StepCurrencyComponent', () => {
  const apiBaseUrl = 'http://localhost:8080';
  let fixture: ComponentFixture<StepCurrencyComponent>;
  let http: HttpTestingController;
  let state: WizardStateService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepCurrencyComponent],
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
  });

  afterEach(() => {
    http.verify();
  });

  function createWithOrderType(orderType: 'TERM' | 'ON_CALL'): ComponentFixture<StepCurrencyComponent> {
    state.initialize({ orderType, skipOrderType: true });
    const componentFixture = TestBed.createComponent(StepCurrencyComponent);
    componentFixture.detectChanges();
    return componentFixture;
  }

  it('shows loading state while fetching currencies', () => {
    fixture = createWithOrderType('TERM');
    expect(fixture.nativeElement.querySelector('[data-testid="currency-loading"]')).toBeTruthy();
    http.expectOne(`${apiBaseUrl}/api/v1/order-creation/term/currencies`).flush({
      tradingDate: '2026-06-07',
      currencies: ['EUR'],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="currency-loading"]')).toBeFalsy();
  });

  it('calls term currencies endpoint for TERM orders', () => {
    fixture = createWithOrderType('TERM');
    const req = http.expectOne(`${apiBaseUrl}/api/v1/order-creation/term/currencies`);
    expect(req.request.method).toBe('GET');
    req.flush({ tradingDate: '2026-06-07', currencies: ['EUR', 'USD'] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('EUR');
    expect(fixture.nativeElement.textContent).toContain('USD');
  });

  it('calls oncall currencies endpoint for ON_CALL orders', () => {
    fixture = createWithOrderType('ON_CALL');
    const req = http.expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/currencies`);
    expect(req.request.method).toBe('GET');
    req.flush({ currencies: ['CHF'] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('CHF');
  });

  it('selecting a currency updates state and emits navigation', () => {
    fixture = createWithOrderType('TERM');
    http.expectOne(`${apiBaseUrl}/api/v1/order-creation/term/currencies`).flush({
      tradingDate: '2026-06-07',
      currencies: ['EUR'],
    });
    fixture.detectChanges();

    const navigated: boolean[] = [];
    fixture.componentInstance.stepComplete.subscribe(() => navigated.push(true));
    fixture.nativeElement.querySelector('[data-testid="currency-EUR"]').click();
    fixture.detectChanges();

    expect(state.state().currency).toBe('EUR');
    expect(state.state().currentStep).toBe(WizardStepId.OPERATION);
    expect(navigated).toEqual([true]);
  });

  it('shows empty state when no currencies are available', () => {
    fixture = createWithOrderType('ON_CALL');
    http.expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/currencies`).flush({ currencies: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="currency-empty"]')).toBeTruthy();
  });

  it('shows error with retry on API failure and reloads on retry', () => {
    fixture = createWithOrderType('TERM');
    http.expectOne(`${apiBaseUrl}/api/v1/order-creation/term/currencies`).flush(
      'Server error',
      { status: 500, statusText: 'Internal Server Error' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="currency-error"]')).toBeTruthy();
    fixture.nativeElement.querySelector('[data-testid="currency-retry"]').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="currency-loading"]')).toBeTruthy();
    http.expectOne(`${apiBaseUrl}/api/v1/order-creation/term/currencies`).flush({
      tradingDate: '2026-06-07',
      currencies: ['EUR'],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="currency-EUR"]')).toBeTruthy();
  });
});
