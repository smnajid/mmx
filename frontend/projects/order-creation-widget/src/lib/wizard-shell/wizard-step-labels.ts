import { WizardStepId } from '../models/wizard-state.model';

export const WIZARD_STEP_LABELS: Record<WizardStepId, string> = {
  [WizardStepId.ORDER_TYPE]: 'Order type',
  [WizardStepId.CURRENCY]: 'Currency',
  [WizardStepId.OPERATION]: 'Operation',
  [WizardStepId.TENOR_OR_NOTICE_PERIOD]: 'Tenor / notice',
  [WizardStepId.COUNTERPARTY]: 'Counterparty',
  [WizardStepId.ORDER_DETAILS]: 'Details',
  [WizardStepId.REVIEW]: 'Review',
};
