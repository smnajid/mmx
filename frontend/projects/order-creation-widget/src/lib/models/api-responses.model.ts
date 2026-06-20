import type { components } from '../generated/trader-orders-views';
import type { NoticePeriod, OrderOperation, OrderType, Tenor } from './order-creation-payload.model';

type Schemas = components['schemas'];

/** ISO 4217 currency code returned by order-creation currency endpoints. */
export type CurrencyOption = string;

export type TenorOption = Tenor;

export type NoticePeriodOption = NoticePeriod;

export type OperationOption = Omit<Schemas['OperationOption'], 'operation'> & {
  operation: OrderOperation;
};

export type CounterpartyOption = Schemas['CounterpartyOption'];

export type TermCurrenciesResponse = Schemas['TermCurrenciesResponse'];

export type OnCallCurrenciesResponse = Schemas['OnCallCurrenciesResponse'];

export type OperationsResponse = Omit<Schemas['OperationsResponse'], 'operations'> & {
  operations: OperationOption[];
};

export type TenorsResponse = Omit<Schemas['TenorsResponse'], 'tenors'> & {
  tenors: TenorOption[];
};

export type NoticePeriodsResponse = Omit<Schemas['NoticePeriodsResponse'], 'noticePeriods'> & {
  noticePeriods: NoticePeriodOption[];
};

export type CounterpartiesResponse = Schemas['CounterpartiesResponse'];

export type ContractInfoResponse = Omit<Schemas['ContractInfoResponse'], 'noticePeriod'> & {
  noticePeriod: NoticePeriod;
};

export type LiveContract = Omit<Schemas['LiveContract'], 'orderType' | 'noticePeriod' | 'tenor'> & {
  orderType: OrderType;
  noticePeriod?: NoticePeriod;
  tenor?: Tenor;
};

export type LiveContractsResponse = Omit<Schemas['LiveContractsResponse'], 'contracts'> & {
  contracts: LiveContract[];
};
