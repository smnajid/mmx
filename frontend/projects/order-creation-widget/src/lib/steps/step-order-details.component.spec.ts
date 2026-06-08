import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WizardStepId } from '../models/wizard-state.model';
import { WizardStateService } from '../services/wizard-state.service';
import { StepOrderDetailsComponent } from './step-order-details.component';

describe('StepOrderDetailsComponent', () => {
  let fixture: ComponentFixture<StepOrderDetailsComponent>;
  let state: WizardStateService;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-07T12:00:00Z'));

    await TestBed.configureTestingModule({
      imports: [StepOrderDetailsComponent],
      providers: [WizardStateService],
    }).compileComponents();

    state = TestBed.inject(WizardStateService);
    state.initialize({ orderType: 'ON_CALL', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('INCREASE', 500000);
    state.completeAndAdvance();
    state.setNoticePeriod('24H');
    state.completeAndAdvance();
    state.setCounterparty('BNKCO', 'BankCo', 3.5, '2026-06-07');
    state.completeAndAdvance();

    fixture = TestBed.createComponent(StepOrderDetailsComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('rejects amount below operation minimum', () => {
    const amountInput = fixture.nativeElement.querySelector('[data-testid="details-amount"]');
    amountInput.value = '100000';
    amountInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const valueDateInput = fixture.nativeElement.querySelector('[data-testid="details-value-date"]');
    valueDateInput.value = '2026-06-10';
    valueDateInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="details-continue"]').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="details-amount-error"]')).toBeTruthy();
    expect(state.state().currentStep).toBe(WizardStepId.ORDER_DETAILS);
  });

  it('rejects valueDate earlier than today plus two calendar days', () => {
    fixture.componentInstance.form.patchValue({
      amount: 600000,
      valueDate: '2026-06-08',
    });
    fixture.detectChanges();

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="details-value-date-error"]')).toBeTruthy();
    expect(state.state().currentStep).toBe(WizardStepId.ORDER_DETAILS);
  });

  it('allows empty minimumRate', () => {
    fixture.componentInstance.form.patchValue({
      amount: 600000,
      valueDate: '2026-06-10',
      minimumRate: null,
    });
    fixture.detectChanges();

    const navigated: boolean[] = [];
    fixture.componentInstance.stepComplete.subscribe(() => navigated.push(true));
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="details-minimum-rate-error"]')).toBeFalsy();
    expect(state.state().minimumRate).toBeUndefined();
    expect(navigated).toEqual([true]);
  });

  it('shows sourceContractNumber for lifecycle operations', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="details-source-contract"]')).toBeTruthy();
  });
});
