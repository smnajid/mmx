import type { WizardStepId } from './wizard-state.model';

export type OrderType = 'TERM' | 'ON_CALL';

export type OrderOperation = 'SUBSCRIPTION' | 'INCREASE' | 'DECREASE' | 'REDEMPTION';

export type Tenor = '1W' | '2W' | '1M' | '3M' | '6M' | '1Y';

export type NoticePeriod = '24H' | '48H';

export interface OrderCreationPayload {
  legalEntityCode: string;
  portfolioNumber: string;
  orderType: OrderType;
  currency: string;
  operation: OrderOperation;
  tenor?: Tenor;
  noticePeriod?: NoticePeriod;
  institutionCode: string;
  counterparty: string;
  amount: number;
  valueDate: string;
  minimumRate?: number;
  sourceContractNumber?: string;
}

export interface WizardStep {
  id: WizardStepId;
  label: string;
}
