import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { OrderOperation } from '../../core/models/order-operation.enum';
import { OrderStatus } from '../../core/models/order-status.enum';
import { OrderType } from '../../core/models/order-type.enum';
import type { OrderSummary } from '../../core/models/order.model';
import { OrderTableComponent } from './order-table.component';

describe('OrderTableComponent', () => {
  let fixture: ComponentFixture<OrderTableComponent>;

  const oneRow = (): OrderSummary => ({
    orderId: 'oid-1',
    externalOrderReference: 'REF-1',
    orderType: OrderType.TERM,
    orderOperation: OrderOperation.SUBSCRIPTION,
    portfolioNumber: 'PF-1',
    currency: 'EUR',
    amount: 1_000,
    valueDate: '2026-08-01',
    minimumRate: null,
    tenor: '3M',
    noticePeriod: null,
    status: OrderStatus.RECEIVED,
    assignedTraderId: null,
    createdAt: '2026-05-01T00:00:00Z',
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderTableComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderTableComponent);
  });

  it('adds ws and queue to View link when listWorkspace and listQueue are set', () => {
    fixture.componentRef.setInput('orders', [oneRow()]);
    fixture.componentRef.setInput('loading', false);
    fixture.componentRef.setInput('listWorkspace', 'term');
    fixture.componentRef.setInput('listQueue', 'assigned');
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a.link') as HTMLAnchorElement | null;
    expect(link).toBeTruthy();
    const href = link!.getAttribute('href') ?? '';
    expect(href).toContain('/orders/oid-1');
    expect(href).toContain('ws=term');
    expect(href).toContain('queue=assigned');
  });

  it('View link has no ws/queue when list context is omitted', () => {
    fixture.componentRef.setInput('orders', [oneRow()]);
    fixture.componentRef.setInput('loading', false);
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a.link') as HTMLAnchorElement | null;
    const href = link?.getAttribute('href') ?? '';
    expect(href).not.toContain('ws=');
    expect(href).not.toContain('queue=');
  });
});
