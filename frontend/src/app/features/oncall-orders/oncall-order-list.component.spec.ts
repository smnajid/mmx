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
import { OnCallOrderListComponent } from './oncall-order-list.component';

describe('OnCallOrderListComponent', () => {
  let fixture: ComponentFixture<OnCallOrderListComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OnCallOrderListComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TraderContextService, useValue: { traderId: signal('oncall-test') } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OnCallOrderListComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('requests received OnCall queue with trader header on init', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url.startsWith('/api/v1/orders/oncall/received') &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '100'
    );
    expect(req.request.headers.get('X-Trader-Id')).toBe('oncall-test');
    req.flush({ content: [], totalElements: 0, page: 0, size: 100 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Received — On call');
  });

  it('renders order reference from paged response', () => {
    fixture.detectChanges();
    const incoming = httpMock.expectOne((req) =>
      req.url.startsWith('/api/v1/orders/oncall/received')
    );
    const row: OrderSummary = {
      orderId: 'oc-1',
      externalOrderReference: 'OC-777',
      orderType: OrderType.ON_CALL,
      orderOperation: OrderOperation.REDEMPTION,
      portfolioNumber: 'PF-O',
      currency: 'USD',
      amount: 250_000,
      valueDate: '2026-05-12',
      minimumRate: 2.5,
      status: OrderStatus.RECEIVED,
      assignedTraderId: null,
      createdAt: '2026-05-03T11:00:00Z',
    };
    incoming.flush({ content: [row], totalElements: 1, page: 0, size: 100 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('OC-777');
    expect(fixture.nativeElement.textContent).toMatch(/2\.5/);
  });
});
