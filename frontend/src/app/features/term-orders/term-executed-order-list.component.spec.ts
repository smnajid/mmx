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
import { TermExecutedOrderListComponent } from './term-executed-order-list.component';

const BASE_ROW: OrderSummary = {
  orderId: 'exec-1',
  externalOrderReference: 'XT-EXEC',
  orderType: OrderType.TERM,
  orderOperation: OrderOperation.SUBSCRIPTION,
  portfolioNumber: 'PF-1',
  currency: 'EUR',
  amount: 1_000_000,
  valueDate: '2026-06-01',
  minimumRate: null,
  tenor: '3M',
  noticePeriod: null,
  status: OrderStatus.EXECUTED,
  counterparty: 'BankCo International',
  assignedTraderId: 'trader-te',
  createdAt: '2026-05-01T10:00:00Z',
};

describe('TermExecutedOrderListComponent', () => {
  let fixture: ComponentFixture<TermExecutedOrderListComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TermExecutedOrderListComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TraderContextService, useValue: { traderId: signal('trader-te') } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TermExecutedOrderListComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushRows(rows: OrderSummary[]): void {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) =>
      r.method === 'GET' && r.url.startsWith('/api/v1/orders/term/executed'),
    );
    req.flush({ content: rows, totalElements: rows.length, page: 0, size: 100 });
    fixture.detectChanges();
  }

  it('loads Term executed queue and shows counterparty column', () => {
    flushRows([BASE_ROW]);
    expect(fixture.nativeElement.textContent).toContain('Counterparty');
    expect(fixture.nativeElement.textContent).toContain('BankCo International');
  });

  it('sends X-Trader-Id header', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) =>
      r.method === 'GET' && r.url.startsWith('/api/v1/orders/term/executed'),
    );
    expect(req.request.headers.get('X-Trader-Id')).toBe('trader-te');
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
