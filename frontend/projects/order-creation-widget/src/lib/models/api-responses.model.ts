import type { NoticePeriod, OrderOperation, OrderType, Tenor } from './order-creation-payload.model';

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
  institutionCode: string;
  counterparty: string;
}

export interface LiveContract {
  contractNumber: string;
  orderType: OrderType;
  currency: string;
  noticePeriod?: NoticePeriod;
  tenor?: Tenor;
  valueDate: string;
  endDate?: string;
  originalAmount: number;
}

export interface LiveContractsResponse {
  contracts: LiveContract[];
}
