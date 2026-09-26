import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { OrderCreationApiService } from '../../core/api/order-creation-api.service';
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
    status: OrderStatus.RECEIVED,
    assignedTraderId: null,
    assignedAt: null,
    executedRate: null,
    institutionCode: null,
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
  const executeOrder = vi.fn();
  const listTermCounterparties = vi.fn();
  const listOnCallCounterparties = vi.fn();
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
    executeOrder,
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    listTermCounterparties.mockReturnValue(
      of({
        counterparties: [
          {
            institutionCode: 'QNB-01',
            displayName: 'QNB',
            rate: 2.15,
            rateDate: '2026-06-20',
            indicative: false,
          },
        ],
      }),
    );
    listOnCallCounterparties.mockReturnValue(
      of({
        counterparties: [
          {
            institutionCode: 'QNB-01',
            displayName: 'QNB',
            rate: 2.15,
            rateDate: '2026-06-20',
            indicative: false,
          },
        ],
      }),
    );

    await TestBed.configureTestingModule({
      imports: [OrderDetailsComponent],
      providers: [
        provideRouter([]),
        { provide: OrderApiService, useValue: apiStub },
        {
          provide: OrderCreationApiService,
          useValue: {
            listTermCounterparties,
            listOnCallCounterparties,
          },
        },
        { provide: TraderContextService, useValue: { traderId: signal('trader-self') } },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ id: 'order-fixture-1' })),
            queryParamMap: of(convertToParamMap({ ws: 'term', queue: 'assigned' })),
            snapshot: {
              paramMap: convertToParamMap({ id: 'order-fixture-1' }),
              queryParamMap: convertToParamMap({ ws: 'term', queue: 'assigned' }),
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderDetailsComponent);
  });

  it('uses back link to the queue from query params', async () => {
    getOrderDetails.mockReturnValue(of(stubDetails({ status: OrderStatus.RECEIVED })));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const back = fixture.nativeElement.querySelector('.crumb a') as HTMLAnchorElement | null;
    expect(back?.getAttribute('href')).toContain('/term/assigned');
  });

  it('shows receive-phase actions for RECEIVED', async () => {
    getOrderDetails.mockReturnValue(of(stubDetails({ status: OrderStatus.RECEIVED })));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Back to Assigned');
    expect(text).toContain('Assign to me');
    expect(text).toContain('Cancel order');
    expect(text).toContain('Reject');
    expect(fixture.nativeElement.querySelectorAll('.actions .btn.danger-outline')).toHaveLength(1);
  });

  it('shows unassign, reject, and execution for ASSIGNED order owned by current trader', async () => {
    getOrderDetails.mockReturnValue(
      of(
        stubDetails({
          status: OrderStatus.ASSIGNED,
          assignedTraderId: 'trader-self',
          counterparty: 'QNB',
          institutionCode: 'QNB-01',
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
    expect(text).toContain('QNB');
    expect(text).toContain('QNB-01');
    expect(fixture.nativeElement.querySelectorAll('.actions .btn.danger-outline')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('mmx-order-execution-form')).toBeTruthy();
  });

  it('execute calls API with rate-only body', async () => {
    getOrderDetails.mockReturnValue(
      of(
        stubDetails({
          status: OrderStatus.ASSIGNED,
          assignedTraderId: 'trader-self',
          counterparty: 'QNB',
          institutionCode: 'QNB-01',
          orderType: OrderType.ON_CALL,
          tenor: null,
          noticePeriod: '48H',
        })
      )
    );
    executeOrder.mockReturnValue(
      of(
        stubDetails({
          status: OrderStatus.EXECUTED,
          executedRate: 2.2,
          counterparty: 'QNB',
          institutionCode: 'QNB-01',
        })
      )
    );
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const form = fixture.debugElement.query(
      (el) => el.name === 'mmx-order-execution-form'
    )?.componentInstance as { onSubmit: () => void; rateModel: string } | undefined;
    expect(form).toBeTruthy();
    form!.rateModel = '2.2';
    form!.onSubmit();

    expect(executeOrder).toHaveBeenCalledWith('order-fixture-1', 'trader-self', {
      executedRate: 2.2,
    });
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
    expect(fixture.nativeElement.textContent).not.toContain('Record execution');
    expect(fixture.nativeElement.textContent).not.toContain('Execute order');
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
