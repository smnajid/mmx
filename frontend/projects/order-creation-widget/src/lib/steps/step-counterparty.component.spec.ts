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
import { StepCounterpartyComponent } from './step-counterparty.component';

describe('StepCounterpartyComponent', () => {
  const apiBaseUrl = 'http://localhost:8080';
  let fixture: ComponentFixture<StepCounterpartyComponent>;
  let http: HttpTestingController;
  let state: WizardStateService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepCounterpartyComponent],
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

  it('lists Term counterparties sorted by rate descending', () => {
    state.initialize({ orderType: 'TERM', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('SUBSCRIPTION', 100000);
    state.completeAndAdvance();
    state.setTenor('3M');
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepCounterpartyComponent);
    fixture.detectChanges();

    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/term/counterparties?currency=EUR&tenor=3M`)
      .flush({
        counterparties: [
          {
            institutionCode: 'LOW',
            displayName: 'Low Bank',
            rate: 2.5,
            rateDate: '2026-06-07',
            indicative: false,
          },
          {
            institutionCode: 'HIGH',
            displayName: 'High Bank',
            rate: 4.1,
            rateDate: '2026-06-07',
            indicative: false,
          },
        ],
      });
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('[data-testid^="counterparty-"]');
    expect(rows[0].textContent).toContain('High Bank');
    expect(rows[1].textContent).toContain('Low Bank');
  });

  it('renders indicative warning for stale rates', () => {
    state.initialize({ orderType: 'TERM', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('SUBSCRIPTION', 100000);
    state.completeAndAdvance();
    state.setTenor('3M');
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepCounterpartyComponent);
    fixture.detectChanges();

    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/term/counterparties?currency=EUR&tenor=3M`)
      .flush({
        counterparties: [
          {
            institutionCode: 'BNKCO',
            displayName: 'BankCo',
            rate: 3.1,
            rateDate: '2026-06-01',
            indicative: true,
          },
        ],
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="counterparty-indicative-BNKCO"]')).toBeTruthy();
  });

  it('stores institutionCode and display name on selection', () => {
    state.initialize({ orderType: 'TERM', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('SUBSCRIPTION', 100000);
    state.completeAndAdvance();
    state.setTenor('3M');
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepCounterpartyComponent);
    fixture.detectChanges();

    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/term/counterparties?currency=EUR&tenor=3M`)
      .flush({
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
    fixture.detectChanges();

    const navigated: boolean[] = [];
    fixture.componentInstance.stepComplete.subscribe(() => navigated.push(true));
    fixture.nativeElement.querySelector('[data-testid="counterparty-BNKCO"]').click();
    fixture.detectChanges();

    expect(state.state().institutionCode).toBe('BNKCO');
    expect(state.state().counterparty).toBe('BankCo');
    expect(state.state().currentStep).toBe(WizardStepId.ORDER_DETAILS);
    expect(navigated).toEqual([true]);
  });
});
