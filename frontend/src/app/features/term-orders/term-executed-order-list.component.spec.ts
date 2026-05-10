import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { OrderOperation } from '../../core/models/order-operation.enum';
import { OrderStatus } from '../../core/models/order-status.enum';
import { OrderType } from '../../core/models/order-type.enum';
import type { OrderSummary } from '../../core/models/order.model';
import { TraderContextService } from '../../core/trader/trader-context.service';
import { TermExecutedOrderListComponent } from './term-executed-order-list.component';

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

  it('loads Term executed queue and shows counterparty column', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) =>
      r.method === 'GET' && r.url.startsWith('/api/v1/orders/term/executed')
    );
    expect(req.request.headers.get('X-Trader-Id')).toBe('trader-te');

    const row: OrderSummary = {
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
    req.flush({ content: [row], totalElements: 1, page: 0, size: 100 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Counterparty');
    expect(fixture.nativeElement.textContent).toContain('BankCo International');
  });
});
