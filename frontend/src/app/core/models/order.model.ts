import { OrderType } from './order-type.enum';
import { OrderOperation } from './order-operation.enum';
import { OrderStatus } from './order-status.enum';
import { HandoffStatus } from './handoff-status.enum';

export interface OrderSummary {
  orderId: string;
  externalOrderReference: string;
  orderType: OrderType;
  orderOperation: OrderOperation;
  portfolioNumber: string;
  currency: string;
  amount: number;
  valueDate: string;
  minimumRate: number | null;
  /** Present for term orders when set at intake; null otherwise. */
  tenor: string | null;
  /** Present for on-call orders when set at intake; null otherwise. */
  noticePeriod: string | null;
  status: OrderStatus;
  /** Execution counterparty; present on executed summaries when omit-null exposes it from the API. */
  counterparty?: string | null;
  /** EXECUTED workspace executed-list rows only; back-office Kafka handoff delivery state. */
  handoffStatus?: HandoffStatus | null;
  assignedTraderId: string | null;
  createdAt: string;
}

export interface OrderDetails {
  orderId: string;
  externalOrderReference: string;
  orderType: OrderType;
  orderOperation: OrderOperation;
  portfolioNumber: string;
  currency: string;
  amount: number;
  valueDate: string;
  minimumRate: number | null;
  tenor: string | null;
  noticePeriod: string | null;
  sourceContractNumber: string | null;
  desiredCounterpartyComment: string | null;
  status: OrderStatus;
  assignedTraderId: string | null;
  assignedAt: string | null;
  executedRate: number | null;
  counterparty: string | null;
  executionTime: string | null;
  dealingReference: string | null;
  generatedContractNumber: string | null;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReceiveOrderRequest {
  externalOrderReference: string;
  orderType: OrderType;
  orderOperation: OrderOperation;
  portfolioNumber: string;
  currency: string;
  amount: number;
  valueDate: string;
  minimumRate?: number | null;
  tenor?: string | null;
  noticePeriod?: string | null;
  sourceContractNumber?: string | null;
  desiredCounterpartyComment?: string | null;
}

export interface AssignOrderRequest {
  traderId: string;
}

export interface UpdateOrderRequest {
  amount?: number;
  valueDate?: string;
}

export interface ExecuteOrderRequest {
  executedRate: number;
  institutionCode: string;
}

export interface RejectOrderRequest {
  reason: string;
}

export interface ReceiveOrderResponse {
  orderId: string;
  status: OrderStatus;
}

export interface ErrorDetail {
  field: string;
  message: string;
}

export interface ApiError {
  error: string;
  message: string;
  details: ErrorDetail[];
}
