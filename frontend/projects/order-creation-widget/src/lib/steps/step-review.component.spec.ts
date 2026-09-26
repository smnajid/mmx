import { ComponentFixture, TestBed } from '@angular/core/testing';
import type { OrderCreationPayload } from '../models/order-creation-payload.model';
import { WizardHostConfigService } from '../services/wizard-host-config.service';
import { WizardStateService } from '../services/wizard-state.service';
import { StepReviewComponent } from './step-review.component';

describe('StepReviewComponent', () => {
  let fixture: ComponentFixture<StepReviewComponent>;
  let state: WizardStateService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepReviewComponent],
      providers: [
        WizardStateService,
        { provide: WizardHostConfigService, useValue: { legalEntityCode: 'LOC' } },
      ],
    }).compileComponents();

    state = TestBed.inject(WizardStateService);
    state.initialize({ orderType: 'TERM', skipOrderType: true });
    state.setCurrency('EUR');
    state.completeAndAdvance();
    state.setOperation('SUBSCRIPTION', 500000);
    state.completeAndAdvance();
    state.setTenor('3M');
    state.completeAndAdvance();
    state.setCounterparty('BNKCO', 'BankCo', 3.5, '2026-06-07');
    state.completeAndAdvance();
    state.setOrderDetails(1000000, '2026-06-10', 3.25);
    state.completeAndAdvance();

    fixture = TestBed.createComponent(StepReviewComponent);
    fixture.componentRef.setInput('portfolioNumber', 'PF-001');
    fixture.detectChanges();
  });

  it('renders a summary of all selections', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('PF-001');
    expect(text).toContain('TERM');
    expect(text).toContain('EUR');
    expect(text).toContain('SUBSCRIPTION');
    expect(text).toContain('3M');
    expect(text).toContain('BankCo');
    expect(text).toContain('1,000,000');
    expect(text).toContain('2026-06-10');
    expect(text).toContain('3.25');
  });

  it('emits orderReady with complete payload when Create Order is clicked', () => {
    const payloads: OrderCreationPayload[] = [];
    fixture.componentInstance.orderReady.subscribe((payload) => payloads.push(payload));

    fixture.nativeElement.querySelector('[data-testid="review-create-order"]').click();
    fixture.detectChanges();

    expect(payloads).toEqual([
      {
        legalEntityCode: 'LOC',
        portfolioNumber: 'PF-001',
        orderType: 'TERM',
        currency: 'EUR',
        operation: 'SUBSCRIPTION',
        tenor: '3M',
        institutionCode: 'BNKCO',
        counterparty: 'BankCo',
        amount: 1000000,
        valueDate: '2026-06-10',
        minimumRate: 3.25,
      },
    ]);
  });

  it('emits cancelled when Cancel is clicked', () => {
    const cancelled: boolean[] = [];
    fixture.componentInstance.cancelled.subscribe(() => cancelled.push(true));

    fixture.nativeElement.querySelector('[data-testid="review-cancel"]').click();
    fixture.detectChanges();

    expect(cancelled).toEqual([true]);
  });
});
