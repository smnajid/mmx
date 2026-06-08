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
import { StepOperationComponent } from './step-operation.component';

describe('StepOperationComponent', () => {
  const apiBaseUrl = 'http://localhost:8080';
  let fixture: ComponentFixture<StepOperationComponent>;
  let http: HttpTestingController;
  let state: WizardStateService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepOperationComponent],
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

  it('displays operations with minimum amounts', () => {
    state.initialize({ orderType: 'TERM', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepOperationComponent);
    fixture.detectChanges();

    http
      .expectOne(`${apiBaseUrl}/api/v1/order-creation/term/operations?currency=EUR`)
      .flush({ operations: [{ operation: 'SUBSCRIPTION', minAmount: 500000 }] });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Subscription');
    expect(fixture.nativeElement.textContent).toContain('500,000');
  });

  it('filters out SUBSCRIPTION in contract-shortcut mode', () => {
    state.applyContractShortcut('EUR', '24H');
    fixture = TestBed.createComponent(StepOperationComponent);
    fixture.detectChanges();

    http.expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/operations?currency=EUR`).flush({
      operations: [
        { operation: 'SUBSCRIPTION', minAmount: 100000 },
        { operation: 'INCREASE', minAmount: 50000 },
        { operation: 'DECREASE', minAmount: 50000 },
        { operation: 'REDEMPTION', minAmount: 50000 },
      ],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Subscription');
    expect(fixture.nativeElement.textContent).toContain('Increase');
    expect(fixture.nativeElement.querySelector('[data-testid="operation-INCREASE"]')).toBeTruthy();
  });

  it('selecting an operation updates state and emits navigation', () => {
    state.initialize({ orderType: 'ON_CALL', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    fixture = TestBed.createComponent(StepOperationComponent);
    fixture.detectChanges();

    http.expectOne(`${apiBaseUrl}/api/v1/order-creation/oncall/operations?currency=EUR`).flush({
      operations: [{ operation: 'SUBSCRIPTION', minAmount: 250000 }],
    });
    fixture.detectChanges();

    const navigated: boolean[] = [];
    fixture.componentInstance.stepComplete.subscribe(() => navigated.push(true));
    fixture.nativeElement.querySelector('[data-testid="operation-SUBSCRIPTION"]').click();
    fixture.detectChanges();

    expect(state.state().operation).toBe('SUBSCRIPTION');
    expect(state.state().operationMinAmount).toBe(250000);
    expect(state.state().currentStep).toBe(WizardStepId.TENOR_OR_NOTICE_PERIOD);
    expect(navigated).toEqual([true]);
  });
});
