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
import { AssignedOrderListComponent } from './assigned-order-list.component';

describe('AssignedOrderListComponent', () => {
  let fixture: ComponentFixture<AssignedOrderListComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssignedOrderListComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TraderContextService, useValue: { traderId: signal('alice') } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AssignedOrderListComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('requests assigned orders for current trader on init', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne(
      (r) =>
        r.method === 'GET' &&
        r.url.startsWith('/api/v1/orders/oncall/assigned') &&
        r.params.get('page') === '0'
    );
    expect(req.request.headers.get('X-Trader-Id')).toBe('alice');
    req.flush({ content: [], totalElements: 0, page: 0, size: 100 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Assigned to you');
  });

  it('shows unassign control for ASSIGNED rows', () => {
    fixture.detectChanges();
    const incoming = httpMock.expectOne((req) => req.url.startsWith('/api/v1/orders/oncall/assigned'));
    const row: OrderSummary = {
      orderId: 'a-1',
      externalOrderReference: 'ASG-001',
      orderType: OrderType.TERM,
      orderOperation: OrderOperation.INCREASE,
      portfolioNumber: 'PF-X',
      currency: 'EUR',
      amount: 1_000,
      valueDate: '2026-07-01',
      minimumRate: null,
      tenor: null,
      noticePeriod: null,
      status: OrderStatus.ASSIGNED,
      assignedTraderId: 'alice',
      createdAt: '2026-05-03T08:00:00Z',
    };
    incoming.flush({ content: [row], totalElements: 1, page: 0, size: 100 });
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector(
      '.btn-action.secondary'
    ) as HTMLButtonElement | null;
    expect(btn?.textContent?.trim()).toBe('Unassign');
  });
});
