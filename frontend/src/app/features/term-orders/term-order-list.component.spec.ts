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
import { TermOrderListComponent } from './term-order-list.component';

describe('TermOrderListComponent', () => {
  let fixture: ComponentFixture<TermOrderListComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TermOrderListComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TraderContextService, useValue: { traderId: signal('trader-unit') } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TermOrderListComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('requests received Term queue with trader header on init', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url.startsWith('/api/v1/orders/term/received') &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '100'
    );
    expect(req.request.headers.get('X-Trader-Id')).toBe('trader-unit');
    req.flush({ content: [], totalElements: 0, page: 0, size: 100 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No orders in this queue.');
  });

  it('renders rows when API returns content', () => {
    fixture.detectChanges();
    const incoming = httpMock.expectOne((req) =>
      req.url.startsWith('/api/v1/orders/term/received')
    );
    const row: OrderSummary = {
      orderId: 'o1',
      externalOrderReference: 'EXT-T1',
      orderType: OrderType.TERM,
      orderOperation: OrderOperation.SUBSCRIPTION,
      portfolioNumber: 'PF-1',
      currency: 'EUR',
      amount: 5_000_000,
      valueDate: '2026-06-15',
      minimumRate: null,
      tenor: '3M',
      status: OrderStatus.RECEIVED,
      assignedTraderId: null,
      createdAt: '2026-05-03T09:00:00Z',
    };
    incoming.flush({ content: [row], totalElements: 1, page: 0, size: 100 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('EXT-T1');
    expect(fixture.nativeElement.querySelector('.btn-action')?.textContent?.trim()).toBe('Assign');
  });
});
