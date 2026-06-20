import type { OrderCreationPayload } from 'order-creation-widget';
import type { ReceiveOrderRequest } from '../../core/models/order.model';
import { OrderOperation } from '../../core/models/order-operation.enum';
import { OrderType } from '../../core/models/order-type.enum';

export function mapOrderCreationPayloadToReceiveRequest(
  payload: OrderCreationPayload,
  externalOrderReference: string,
): ReceiveOrderRequest {
  const request: ReceiveOrderRequest = {
    externalOrderReference,
    orderType: payload.orderType as OrderType,
    orderOperation: payload.operation as OrderOperation,
    portfolioNumber: payload.portfolioNumber,
    currency: payload.currency,
    amount: payload.amount,
    valueDate: payload.valueDate,
    institutionCode: payload.institutionCode,
  };

  if (payload.minimumRate != null) {
    request.minimumRate = payload.minimumRate;
  }
  if (payload.tenor) {
    request.tenor = payload.tenor;
  }
  if (payload.noticePeriod) {
    request.noticePeriod = payload.noticePeriod;
  }
  if (payload.sourceContractNumber) {
    request.sourceContractNumber = payload.sourceContractNumber;
  }

  return request;
}

export function generatePlaygroundExternalReference(now = new Date()): string {
  const stamp = [
    now.getUTCFullYear(),
    String(now.getUTCMonth() + 1).padStart(2, '0'),
    String(now.getUTCDate()).padStart(2, '0'),
    String(now.getUTCHours()).padStart(2, '0'),
    String(now.getUTCMinutes()).padStart(2, '0'),
    String(now.getUTCSeconds()).padStart(2, '0'),
  ].join('');
  const suffix = Math.random().toString(36).slice(2, 6).toUpperCase();
  return `PLAYGROUND-${stamp}-${suffix}`;
}
