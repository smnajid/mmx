import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { OrderOperation } from '../../core/models/order-operation.enum';
import { OrderStatus } from '../../core/models/order-status.enum';
import { OrderType } from '../../core/models/order-type.enum';
import { HandoffStatus } from '../../core/models/handoff-status.enum';
import type { OrderSummary } from '../../core/models/order.model';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { OnCallExecutedOrderListComponent } from './oncall-executed-order-list.component';

const BASE_ROW: OrderSummary = {
  orderId: 'oce-1',
  externalOrderReference: 'XO-EXEC',
  orderType: OrderType.ON_CALL,
  orderOperation: OrderOperation.SUBSCRIPTION,
  portfolioNumber: 'PF-2',
  currency: 'EUR',
  amount: 500_000,
  valueDate: '2026-06-02',
  minimumRate: 2,
  tenor: null,
  noticePeriod: '24H',
  status: OrderStatus.EXECUTED,
  counterparty: 'Prime MM Desk',
  assignedTraderId: 'trader-oc',
  createdAt: '2026-05-02T09:00:00Z',
};

describe('OnCallExecutedOrderListComponent', () => {
  let fixture: ComponentFixture<OnCallExecutedOrderListComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OnCallExecutedOrderListComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TraderContextService, useValue: { traderId: signal('trader-oc') } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OnCallExecutedOrderListComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushRows(rows: OrderSummary[]): void {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) =>
      r.method === 'GET' && r.url.startsWith('/api/v1/orders/oncall/executed'),
    );
    req.flush({ content: rows, totalElements: rows.length, page: 0, size: 100 });
    fixture.detectChanges();
  }

  it('loads OnCall executed queue and renders counterparty', () => {
    flushRows([BASE_ROW]);
    expect(fixture.nativeElement.textContent).toContain('Prime MM Desk');
  });

  it('sends X-User-Id header', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) =>
      r.method === 'GET' && r.url.startsWith('/api/v1/orders/oncall/executed'),
    );
    expect(req.request.headers.get('X-User-Id')).toBe('trader-oc');
    req.flush({ content: [], totalElements: 0, page: 0, size: 100 });
  });

  describe('handoff status column (FR-013)', () => {
    it('renders "Queuing" label and handoff-pending class for PENDING', () => {
      flushRows([{ ...BASE_ROW, orderId: 'p1', handoffStatus: HandoffStatus.PENDING }]);
      const span: HTMLElement | null = fixture.nativeElement.querySelector('.handoff-pending');
      expect(span).not.toBeNull();
      expect(span?.textContent?.trim()).toBe('Queuing');
    });

    it('renders "Sent" label and handoff-published class for PUBLISHED', () => {
      flushRows([{ ...BASE_ROW, orderId: 'p2', handoffStatus: HandoffStatus.PUBLISHED }]);
      const span: HTMLElement | null = fixture.nativeElement.querySelector('.handoff-published');
      expect(span).not.toBeNull();
      expect(span?.textContent?.trim()).toBe('Sent');
    });

    it('renders "Send failed" label and handoff-failed class for FAILED', () => {
      flushRows([{ ...BASE_ROW, orderId: 'p3', handoffStatus: HandoffStatus.FAILED }]);
      const span: HTMLElement | null = fixture.nativeElement.querySelector('.handoff-failed');
      expect(span).not.toBeNull();
      expect(span?.textContent?.trim()).toBe('Send failed');
    });

    it('renders all three handoff states simultaneously when rows carry each status', () => {
      flushRows([
        { ...BASE_ROW, orderId: 'h1', handoffStatus: HandoffStatus.PENDING },
        { ...BASE_ROW, orderId: 'h2', handoffStatus: HandoffStatus.PUBLISHED },
        { ...BASE_ROW, orderId: 'h3', handoffStatus: HandoffStatus.FAILED },
      ]);
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('.handoff-pending')).not.toBeNull();
      expect(el.querySelector('.handoff-published')).not.toBeNull();
      expect(el.querySelector('.handoff-failed')).not.toBeNull();
      expect(el.textContent).toContain('Queuing');
      expect(el.textContent).toContain('Sent');
      expect(el.textContent).toContain('Send failed');
    });

    it('renders em-dash when handoffStatus is absent', () => {
      flushRows([{ ...BASE_ROW, orderId: 'h4', handoffStatus: null }]);
      expect(fixture.nativeElement.querySelector('.handoff-pending')).toBeNull();
      expect(fixture.nativeElement.querySelector('.handoff-published')).toBeNull();
      expect(fixture.nativeElement.querySelector('.handoff-failed')).toBeNull();
    });
  });
});
