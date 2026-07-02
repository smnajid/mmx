import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { WizardStepId } from '../models/wizard-state.model';
import { WizardApiService } from '../services/wizard-api.service';
import { WizardHostConfigService } from '../services/wizard-host-config.service';
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
        { provide: WizardHostConfigService, useValue: { legalEntityCode: 'LOC', apiBaseUrl: '' } },
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
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/term/counterparties?legalEntityCode=LOC&currency=EUR&tenor=3M`)
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
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/term/counterparties?legalEntityCode=LOC&currency=EUR&tenor=3M`)
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

  it('does not render a value-date input', () => {
    state.initialize({ orderType: 'ON_CALL', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('INCREASE', 500000);
    state.completeAndAdvance();
    state.setNoticePeriod('48H');
    state.completeAndAdvance();
    state.setValueDate('2026-06-30');
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepCounterpartyComponent);
    fixture.detectChanges();

    http
      .expectOne(
        `${apiBaseUrl}/api/v1/order-creation/oncall/counterparties?legalEntityCode=LOC&currency=EUR&noticePeriod=48H&valueDate=2026-06-30`,
      )
      .flush({ counterparties: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="counterparty-value-date"]')).toBeFalsy();
  });

  it('loads OnCall counterparties using valueDate from prior step', () => {
    state.initialize({ orderType: 'ON_CALL', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('INCREASE', 500000);
    state.completeAndAdvance();
    state.setNoticePeriod('48H');
    state.completeAndAdvance();
    state.setValueDate('2026-06-30');
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepCounterpartyComponent);
    fixture.detectChanges();

    http
      .expectOne(
        `${apiBaseUrl}/api/v1/order-creation/oncall/counterparties?legalEntityCode=LOC&currency=EUR&noticePeriod=48H&valueDate=2026-06-30`,
      )
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

    expect(fixture.nativeElement.textContent).toContain('BankCo');
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
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/term/counterparties?legalEntityCode=LOC&currency=EUR&tenor=3M`)
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

  it('shortcut INCREASE filters to contract institution and blocks when no rate', () => {
    state.applyContractShortcut('EUR', '24H', 'CT-00042', 'BNKCO', 'BankCo');
    state.setOperation('INCREASE', 50000);
    state.completeAndAdvance();
    state.setValueDate('2026-06-30');
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepCounterpartyComponent);
    fixture.detectChanges();

    http
      .expectOne(
        `${apiBaseUrl}/api/v1/order-creation/oncall/counterparties?legalEntityCode=LOC&currency=EUR&noticePeriod=24H&valueDate=2026-06-30`,
      )
      .flush({
        counterparties: [
          {
            institutionCode: 'OTHER',
            displayName: 'Other Bank',
            rate: 4.0,
            rateDate: '2026-06-07',
            indicative: false,
          },
        ],
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="counterparty-empty"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('No rate is available');
    expect(fixture.nativeElement.querySelector('[data-testid="counterparty-BNKCO"]')).toBeFalsy();
  });

  it('shortcut DECREASE shows locked institution and allows continue without rate', () => {
    state.applyContractShortcut('EUR', '24H', 'CT-00042', 'BNKCO', 'BankCo');
    state.setOperation('DECREASE', 50000);
    state.completeAndAdvance();
    state.setValueDate('2026-06-30');
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepCounterpartyComponent);
    fixture.detectChanges();

    http
      .expectOne(
        `${apiBaseUrl}/api/v1/order-creation/oncall/counterparties?legalEntityCode=LOC&currency=EUR&noticePeriod=24H&valueDate=2026-06-30`,
      )
      .flush({ counterparties: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="counterparty-locked"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('BankCo');
    expect(fixture.nativeElement.querySelector('[data-testid="counterparty-no-rate"]')).toBeTruthy();

    const navigated: boolean[] = [];
    fixture.componentInstance.stepComplete.subscribe(() => navigated.push(true));
    fixture.nativeElement.querySelector('[data-testid="counterparty-continue-locked"]').click();
    fixture.detectChanges();

    expect(state.state().institutionCode).toBe('BNKCO');
    expect(state.state().counterparty).toBe('BankCo');
    expect(state.state().counterpartyRate).toBeUndefined();
    expect(state.state().currentStep).toBe(WizardStepId.ORDER_DETAILS);
    expect(navigated).toEqual([true]);
  });

  it('shortcut REDEMPTION shows locked institution and allows continue without rate', () => {
    state.applyContractShortcut('EUR', '24H', 'CT-00042', 'BNKCO', 'BankCo');
    state.setOperation('REDEMPTION', 50000);
    state.completeAndAdvance();
    state.setValueDate('2026-06-30');
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepCounterpartyComponent);
    fixture.detectChanges();

    http
      .expectOne(
        `${apiBaseUrl}/api/v1/order-creation/oncall/counterparties?legalEntityCode=LOC&currency=EUR&noticePeriod=24H&valueDate=2026-06-30`,
      )
      .flush({ counterparties: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="counterparty-continue-locked"]')).toBeTruthy();
    fixture.nativeElement.querySelector('[data-testid="counterparty-continue-locked"]').click();
    fixture.detectChanges();

    expect(state.state().institutionCode).toBe('BNKCO');
    expect(state.state().counterpartyRate).toBeUndefined();
  });
});
