import type { OrderDetails } from '../../core/models/order.model';
import { OrderStatus } from '../../core/models/order-status.enum';

export type OrderDetailAction =
  | 'assign'
  | 'cancel'
  | 'reject'
  | 'unassign'
  | 'update'
  | 'execute';

export function allowedActions(order: OrderDetails, traderId: string): ReadonlySet<OrderDetailAction> {
  const actions = new Set<OrderDetailAction>();

  switch (order.status) {
    case OrderStatus.RECEIVED:
      actions.add('assign');
      actions.add('cancel');
      actions.add('reject');
      break;
    case OrderStatus.ASSIGNED:
      actions.add('unassign');
      if (order.assignedTraderId === traderId) {
        actions.add('reject');
        actions.add('update');
        actions.add('execute');
      }
      break;
    default:
      break;
  }

  return actions;
}

export function canShowAction(
  order: OrderDetails | null,
  traderId: string,
  action: OrderDetailAction
): boolean {
  if (!order) {
    return false;
  }
  return allowedActions(order, traderId).has(action);
}
