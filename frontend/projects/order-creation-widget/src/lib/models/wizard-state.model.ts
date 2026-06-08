import type {
  NoticePeriod,
  OrderOperation,
  OrderType,
  Tenor,
} from './order-creation-payload.model';

export enum WizardStepId {
  ORDER_TYPE = 'ORDER_TYPE',
  CURRENCY = 'CURRENCY',
  OPERATION = 'OPERATION',
  TENOR_OR_NOTICE_PERIOD = 'TENOR_OR_NOTICE_PERIOD',
  COUNTERPARTY = 'COUNTERPARTY',
  ORDER_DETAILS = 'ORDER_DETAILS',
  REVIEW = 'REVIEW',
}

export interface WizardState {
  currentStep: WizardStepId;
  completedSteps: WizardStepId[];
  skipOrderTypeStep: boolean;
  orderType?: OrderType;
  currency?: string;
  operation?: OrderOperation;
  operationMinAmount?: number;
  tenor?: Tenor;
  noticePeriod?: NoticePeriod;
  institutionCode?: string;
  counterparty?: string;
  counterpartyRate?: number;
  counterpartyRateDate?: string;
  amount?: number;
  valueDate?: string;
  minimumRate?: number;
  sourceContractNumber?: string;
  contractShortcut: boolean;
}
