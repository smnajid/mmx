import type { NoticePeriod, OrderOperation, Tenor } from './order-creation-payload.model';

/** ISO 4217 currency code returned by order-creation currency endpoints. */
export type CurrencyOption = string;

export type TenorOption = Tenor;

export type NoticePeriodOption = NoticePeriod;

export interface OperationOption {
  operation: OrderOperation;
  minAmount: number;
}

export interface CounterpartyOption {
  institutionCode: string;
  displayName: string;
  rate: number;
  rateDate: string;
  indicative: boolean;
}

export interface TermCurrenciesResponse {
  tradingDate: string;
  currencies: CurrencyOption[];
}

export interface OnCallCurrenciesResponse {
  currencies: CurrencyOption[];
}

export interface OperationsResponse {
  operations: OperationOption[];
}

export interface TenorsResponse {
  tenors: TenorOption[];
}

export interface NoticePeriodsResponse {
  noticePeriods: NoticePeriodOption[];
}

export interface CounterpartiesResponse {
  counterparties: CounterpartyOption[];
}

export interface ContractInfoResponse {
  currency: string;
  noticePeriod: NoticePeriod;
}
