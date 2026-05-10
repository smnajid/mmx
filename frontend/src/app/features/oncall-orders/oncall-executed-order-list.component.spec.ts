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
import { OnCallExecutedOrderListComponent } from './oncall-executed-order-list.component';

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

  it('loads OnCall executed queue and renders counterparty', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) =>
      r.method === 'GET' && r.url.startsWith('/api/v1/orders/oncall/executed')
    );
    expect(req.request.headers.get('X-Trader-Id')).toBe('trader-oc');

    const row: OrderSummary = {
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
    req.flush({ content: [row], totalElements: 1, page: 0, size: 100 });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Prime MM Desk');
  });
});
