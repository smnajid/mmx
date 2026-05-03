import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { OrderApiService } from '../../core/api/order-api.service';
import type { OrderDetails } from '../../core/models/order.model';
import { OrderOperation } from '../../core/models/order-operation.enum';
import { OrderStatus } from '../../core/models/order-status.enum';
import { OrderType } from '../../core/models/order-type.enum';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { OrderDetailsComponent } from './order-details.component';

function stubDetails(overrides: Partial<OrderDetails> = {}): OrderDetails {
  return {
    orderId: 'order-fixture-1',
    externalOrderReference: 'MMX-DTLS-UNIT',
    orderType: OrderType.TERM,
    orderOperation: OrderOperation.SUBSCRIPTION,
    portfolioNumber: 'PF-U',
    currency: 'EUR',
    amount: 2_000_000,
    valueDate: '2026-08-01',
    minimumRate: null,
    tenor: '6M',
    noticePeriod: null,
    sourceContractNumber: null,
    desiredCounterpartyComment: null,
    status: OrderStatus.RECEIVED,
    assignedTraderId: null,
    assignedAt: null,
    executedRate: null,
    counterparty: null,
    executionTime: null,
    dealingReference: null,
    generatedContractNumber: null,
    rejectionReason: null,
    createdAt: '2026-05-03T07:00:00Z',
    updatedAt: '2026-05-03T07:00:00Z',
    ...overrides,
  };
}

describe('OrderDetailsComponent', () => {
  let fixture: ComponentFixture<OrderDetailsComponent>;

  const getOrderDetails = vi.fn();
  const apiStub: Pick<
    OrderApiService,
    | 'getOrderDetails'
    | 'assignOrder'
    | 'unassignOrder'
    | 'cancelOrder'
    | 'rejectOrder'
    | 'updateOrder'
    | 'executeOrder'
  > = {
    getOrderDetails,
    assignOrder: vi.fn(),
    unassignOrder: vi.fn(),
    cancelOrder: vi.fn(),
    rejectOrder: vi.fn(),
    updateOrder: vi.fn(),
    executeOrder: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [OrderDetailsComponent],
      providers: [
        provideRouter([]),
        { provide: OrderApiService, useValue: apiStub },
        { provide: TraderContextService, useValue: { traderId: signal('trader-self') } },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: 'order-fixture-1' })) },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderDetailsComponent);
  });

  it('shows receive-phase actions for RECEIVED', async () => {
    getOrderDetails.mockReturnValue(of(stubDetails({ status: OrderStatus.RECEIVED })));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Assign to me');
    expect(text).toContain('Cancel order');
    expect(text).toContain('Reject');
  });

  it('shows unassign, reject, and execution for ASSIGNED order owned by current trader', async () => {
    getOrderDetails.mockReturnValue(
      of(
        stubDetails({
          status: OrderStatus.ASSIGNED,
          assignedTraderId: 'trader-self',
        })
      )
    );
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Unassign');
    expect(text).toContain('Reject');
    expect(text).toContain('Record execution');
    expect(text).toContain('Execute order');
    expect(fixture.nativeElement.querySelector('.actions .btn.danger-outline')).toBeTruthy();
  });

  it('does not show reject for ASSIGNED order owned by another trader', async () => {
    getOrderDetails.mockReturnValue(
      of(
        stubDetails({
          status: OrderStatus.ASSIGNED,
          assignedTraderId: 'someone-else',
        })
      )
    );
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Unassign');
    expect(fixture.nativeElement.querySelector('.actions .btn.danger-outline')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Record execution');
  });

  it('hides receive and assignment actions for EXECUTED', async () => {
    getOrderDetails.mockReturnValue(
      of(
        stubDetails({
          status: OrderStatus.EXECUTED,
          assignedTraderId: 'trader-self',
          executedRate: 3.4,
          counterparty: 'BankCo',
          dealingReference: 'DR-1',
          generatedContractNumber: 'CN-9',
          executionTime: '2026-05-03T12:00:00Z',
        })
      )
    );
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('EXECUTED');
    expect(text).not.toContain('Assign to me');
    expect(text).not.toContain('Record execution');
  });
});
