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
import { StepTenorNoticePeriodComponent } from './step-tenor-notice-period.component';

describe('StepTenorNoticePeriodComponent', () => {
  const apiBaseUrl = 'http://localhost:8080';
  let fixture: ComponentFixture<StepTenorNoticePeriodComponent>;
  let http: HttpTestingController;
  let state: WizardStateService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepTenorNoticePeriodComponent],
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

  it('loads and displays tenors for Term orders', () => {
    state.initialize({ orderType: 'TERM', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('SUBSCRIPTION', 100000);
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepTenorNoticePeriodComponent);
    fixture.detectChanges();

    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/term/tenors?currency=EUR`)
      .flush({ tenors: ['1M', '3M'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('1M');
    expect(fixture.nativeElement.textContent).toContain('3M');
  });

  it('loads and displays notice periods for OnCall orders', () => {
    state.initialize({ orderType: 'ON_CALL', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('SUBSCRIPTION', 100000);
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepTenorNoticePeriodComponent);
    fixture.detectChanges();

    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/notice-periods?currency=EUR`)
      .flush({ noticePeriods: ['24H', '48H'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('24H');
    expect(fixture.nativeElement.textContent).toContain('48H');
  });

  it('skips API call in contract shortcut mode', () => {
    state.applyContractShortcut('EUR', '24H');
    fixture = TestBed.createComponent(StepTenorNoticePeriodComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="tenor-skipped"]')).toBeTruthy();
    http.expectNone(`${apiBaseUrl}/api/v1/order-creation/oncall/notice-periods?currency=EUR`);
  });

  it('selecting a tenor updates state and emits navigation', () => {
    state.initialize({ orderType: 'TERM', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('SUBSCRIPTION', 100000);
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepTenorNoticePeriodComponent);
    fixture.detectChanges();

    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/term/tenors?currency=EUR`)
      .flush({ tenors: ['3M'] });
    fixture.detectChanges();

    const navigated: boolean[] = [];
    fixture.componentInstance.stepComplete.subscribe(() => navigated.push(true));
    fixture.nativeElement.querySelector('[data-testid="tenor-3M"]').click();
    fixture.detectChanges();

    expect(state.state().tenor).toBe('3M');
    expect(state.state().currentStep).toBe(WizardStepId.COUNTERPARTY);
    expect(navigated).toEqual([true]);
  });
});
