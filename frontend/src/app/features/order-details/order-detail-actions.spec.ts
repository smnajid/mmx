import { describe, expect, it } from 'vitest';
import type { OrderDetails } from '../../core/models/order.model';
import { OrderOperation } from '../../core/models/order-operation.enum';
import { OrderStatus } from '../../core/models/order-status.enum';
import { OrderType } from '../../core/models/order-type.enum';
import { allowedActions } from './order-detail-actions';

function base(overrides: Partial<OrderDetails> = {}): OrderDetails {
  return {
    orderId: 'o1',
    externalOrderReference: 'REF-1',
    orderType: OrderType.TERM,
    orderOperation: OrderOperation.SUBSCRIPTION,
    portfolioNumber: 'PF-1',
    currency: 'EUR',
    amount: 1_000_000,
    valueDate: '2026-06-01',
    minimumRate: null,
    tenor: '3M',
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
    createdAt: '2026-05-01T00:00:00Z',
    updatedAt: '2026-05-01T00:00:00Z',
    ...overrides,
  };
}

describe('allowedActions', () => {
  it('RECEIVED allows assign, cancel, reject', () => {
    const actions = allowedActions(base(), 'trader-a');
    expect(actions.has('assign')).toBe(true);
    expect(actions.has('cancel')).toBe(true);
    expect(actions.has('reject')).toBe(true);
    expect(actions.has('execute')).toBe(false);
  });

  it('ASSIGNED assignee allows sensitive actions', () => {
    const actions = allowedActions(
      base({ status: OrderStatus.ASSIGNED, assignedTraderId: 'trader-a', assignedAt: '2026-05-02T00:00:00Z' }),
      'trader-a'
    );
    expect(actions.has('unassign')).toBe(true);
    expect(actions.has('reject')).toBe(true);
    expect(actions.has('update')).toBe(true);
    expect(actions.has('execute')).toBe(true);
  });

  it('ASSIGNED non-assignee allows only unassign', () => {
    const actions = allowedActions(
      base({ status: OrderStatus.ASSIGNED, assignedTraderId: 'other', assignedAt: '2026-05-02T00:00:00Z' }),
      'trader-a'
    );
    expect(actions.has('unassign')).toBe(true);
    expect(actions.has('reject')).toBe(false);
    expect(actions.has('execute')).toBe(false);
  });

  it('EXECUTED offers no trader actions', () => {
    const actions = allowedActions(
      base({
        status: OrderStatus.EXECUTED,
        assignedTraderId: 'trader-a',
        executedRate: 3.5,
        counterparty: 'Bank',
      }),
      'trader-a'
    );
    expect(actions.size).toBe(0);
  });
});
