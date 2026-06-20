import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import type { OrderDetails } from '../../core/models/order.model';
import { OrderOperation } from '../../core/models/order-operation.enum';
import { OrderStatus } from '../../core/models/order-status.enum';
import { OrderType } from '../../core/models/order-type.enum';
import { OrderCreationApiService } from '../../core/api/order-creation-api.service';
import { OrderExecutionFormComponent } from './order-execution-form.component';

function stubOrder(overrides: Partial<OrderDetails> = {}): OrderDetails {
  return {
    orderId: 'order-exec-1',
    externalOrderReference: 'MMX-EXEC-UNIT',
    orderType: OrderType.TERM,
    orderOperation: OrderOperation.SUBSCRIPTION,
    portfolioNumber: 'PF-E',
    currency: 'EUR',
    amount: 1_000_000,
    valueDate: '2026-08-01',
    minimumRate: null,
    tenor: '3M',
    noticePeriod: null,
    sourceContractNumber: null,
    status: OrderStatus.ASSIGNED,
    assignedTraderId: 'trader-self',
    assignedAt: '2026-06-01T00:00:00Z',
    executedRate: null,
    institutionCode: 'BNKCO',
    counterparty: 'BankCo',
    executionTime: null,
    dealingReference: null,
    generatedContractNumber: null,
    rejectionReason: null,
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
    ...overrides,
  };
}

describe('OrderExecutionFormComponent', () => {
  let fixture: ComponentFixture<OrderExecutionFormComponent>;
  const listTermCounterparties = vi.fn();
  const listOnCallCounterparties = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    listTermCounterparties.mockReturnValue(
      of({
        counterparties: [
          {
            institutionCode: 'BNKCO',
            displayName: 'BankCo',
            rate: 2.15,
            rateDate: '2026-06-20',
            indicative: false,
          },
        ],
      }),
    );
    listOnCallCounterparties.mockReturnValue(of({ counterparties: [] }));

    await TestBed.configureTestingModule({
      imports: [OrderExecutionFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: OrderCreationApiService,
          useValue: { listTermCounterparties, listOnCallCounterparties },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderExecutionFormComponent);
    fixture.componentRef.setInput('order', stubOrder());
    fixture.detectChanges();
  });

  it('does not show an institution picker', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[name="institutionPicker"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('datalist')).toBeNull();
  });

  it('shows locked counterparty from order input', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('BankCo');
    expect(text).toContain('BNKCO');
  });

  it('pre-fills rate from counterparties mock', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('[name="executedRate"]') as HTMLInputElement;
    expect(input.value).toBe('2.15');
    expect(fixture.nativeElement.textContent).toContain('2026-06-20');
  });

  it('emits executedRate only on submit', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const emitted = vi.fn();
    fixture.componentInstance.submitExecute.subscribe(emitted);
    fixture.componentInstance.onSubmit();
    expect(emitted).toHaveBeenCalledWith({ executedRate: 2.15 });
  });

  it('shows Indicative badge when indicative is true', async () => {
    listTermCounterparties.mockReturnValue(
      of({
        counterparties: [
          {
            institutionCode: 'BNKCO',
            displayName: 'BankCo',
            rate: 2.15,
            rateDate: '2026-06-20',
            indicative: true,
          },
        ],
      }),
    );
    fixture.componentRef.setInput('order', stubOrder());
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="execute-indicative-badge"]')).toBeTruthy();
  });

  it('blocks rate below minimumRate', async () => {
    fixture.componentRef.setInput('order', stubOrder({ minimumRate: 2.0 }));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const emitted = vi.fn();
    fixture.componentInstance.submitExecute.subscribe(emitted);
    fixture.componentInstance.rateModel = '1.99';
    fixture.componentInstance.onSubmit();
    expect(emitted).not.toHaveBeenCalled();
    expect(fixture.componentInstance.localError()).toContain('at least 2');
  });
});
