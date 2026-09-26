import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WizardStepId } from '../models/wizard-state.model';
import { WizardStateService } from '../services/wizard-state.service';
import { StepValueDateComponent } from './step-value-date.component';

describe('StepValueDateComponent', () => {
  let fixture: ComponentFixture<StepValueDateComponent>;
  let state: WizardStateService;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-07T12:00:00Z'));

    await TestBed.configureTestingModule({
      imports: [StepValueDateComponent],
      providers: [WizardStateService],
    }).compileComponents();

    state = TestBed.inject(WizardStateService);
    state.initialize({ orderType: 'ON_CALL', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('INCREASE', 500000);
    state.completeAndAdvance();
    state.setNoticePeriod('48H');
    state.completeAndAdvance();

    fixture = TestBed.createComponent(StepValueDateComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('rejects valueDate earlier than today plus two calendar days', () => {
    fixture.componentInstance.form.patchValue({ valueDate: '2026-06-08' });
    fixture.detectChanges();

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="value-date-error"]')).toBeTruthy();
    expect(state.state().currentStep).toBe(WizardStepId.VALUE_DATE);
  });

  it('persists valueDate and advances on submit', () => {
    fixture.componentInstance.form.patchValue({ valueDate: '2026-06-10' });
    fixture.detectChanges();

    const navigated: boolean[] = [];
    fixture.componentInstance.stepComplete.subscribe(() => navigated.push(true));
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(state.state().valueDate).toBe('2026-06-10');
    expect(state.state().currentStep).toBe(WizardStepId.COUNTERPARTY);
    expect(navigated).toEqual([true]);
  });
});
