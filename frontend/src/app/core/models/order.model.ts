import type { components } from '../api/generated/trader-orders-views';
import { OrderOperation } from './order-operation.enum';
import { OrderStatus } from './order-status.enum';
import { OrderType } from './order-type.enum';
import { HandoffStatus } from './handoff-status.enum';

type Schemas = components['schemas'];

type WithAppOrderEnums<T> = Omit<T, 'orderType' | 'orderOperation' | 'status' | 'handoffStatus'> & {
  orderType: OrderType;
  orderOperation: OrderOperation;
  status: OrderStatus;
  handoffStatus?: HandoffStatus | null;
};

/** Contract-derived types from `contracts/002-trader-orders-views/openapi.yaml`. */
export type ReceiveOrderRequest = Omit<
  Schemas['ReceiveOrderRequest'],
  'orderType' | 'orderOperation'
> & {
  orderType: OrderType;
  orderOperation: OrderOperation;
};
export type ReceiveOrderResponse = Omit<Schemas['ReceiveOrderResponse'], 'status'> & {
  status: OrderStatus;
};
export type UpdateOrderRequest = Schemas['UpdateOrderRequest'];
export type ExecuteOrderRequest = Schemas['ExecuteOrderRequest'];
export type RejectOrderRequest = Schemas['RejectOrderRequest'];
export type OrderSummary = WithAppOrderEnums<Schemas['OrderSummaryResponse']>;
export type OrderDetails = WithAppOrderEnums<Schemas['OrderDetailsResponse']>;
export type ErrorDetail = Schemas['FieldError'];
export type ApiError = Schemas['ErrorResponse'];

/** Not in 002 contract; assign uses `X-Trader-Id` header. */
export interface AssignOrderRequest {
  traderId: string;
}
