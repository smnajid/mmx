import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WizardStepId } from '../models/wizard-state.model';
import { WizardStateService } from '../services/wizard-state.service';
import { StepOrderTypeComponent } from './step-order-type.component';

describe('StepOrderTypeComponent', () => {
  let fixture: ComponentFixture<StepOrderTypeComponent>;
  let state: WizardStateService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepOrderTypeComponent],
      providers: [WizardStateService],
    }).compileComponents();

    state = TestBed.inject(WizardStateService);
    fixture = TestBed.createComponent(StepOrderTypeComponent);
    fixture.detectChanges();
  });

  it('renders Term and OnCall options', () => {
    const buttons = fixture.nativeElement.querySelectorAll('[data-testid^="order-type-"]');
    expect(buttons.length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Term');
    expect(fixture.nativeElement.textContent).toContain('On Call');
  });

  it('selecting Term updates state and emits navigation', () => {
    const navigated: boolean[] = [];
    fixture.componentInstance.stepComplete.subscribe(() => navigated.push(true));

    fixture.nativeElement.querySelector('[data-testid="order-type-term"]').click();
    fixture.detectChanges();

    expect(state.state().orderType).toBe('TERM');
    expect(state.state().currentStep).toBe(WizardStepId.CURRENCY);
    expect(navigated).toEqual([true]);
  });

  it('selecting OnCall updates state and emits navigation', () => {
    const navigated: boolean[] = [];
    fixture.componentInstance.stepComplete.subscribe(() => navigated.push(true));

    fixture.nativeElement.querySelector('[data-testid="order-type-oncall"]').click();
    fixture.detectChanges();

    expect(state.state().orderType).toBe('ON_CALL');
    expect(state.state().currentStep).toBe(WizardStepId.CURRENCY);
    expect(navigated).toEqual([true]);
  });
});
