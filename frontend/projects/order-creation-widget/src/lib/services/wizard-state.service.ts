import { computed, Injectable, signal } from '@angular/core';
import type { NoticePeriod, OrderOperation, OrderType, Tenor } from '../models/order-creation-payload.model';
import { WizardState, WizardStepId } from '../models/wizard-state.model';

const FULL_FLOW_STEPS: WizardStepId[] = [
  WizardStepId.ORDER_TYPE,
  WizardStepId.CURRENCY,
  WizardStepId.OPERATION,
  WizardStepId.TENOR_OR_NOTICE_PERIOD,
  WizardStepId.VALUE_DATE,
  WizardStepId.COUNTERPARTY,
  WizardStepId.ORDER_DETAILS,
  WizardStepId.REVIEW,
];

const CONTRACT_SHORTCUT_STEPS: WizardStepId[] = [
  WizardStepId.OPERATION,
  WizardStepId.VALUE_DATE,
  WizardStepId.COUNTERPARTY,
  WizardStepId.ORDER_DETAILS,
  WizardStepId.REVIEW,
];

export function createInitialWizardState(overrides: Partial<WizardState> = {}): WizardState {
  return {
    currentStep: WizardStepId.ORDER_TYPE,
    completedSteps: [],
    skipOrderTypeStep: false,
    contractShortcut: false,
    ...overrides,
  };
}

@Injectable()
export class WizardStateService {
  private readonly stateSignal = signal<WizardState>(createInitialWizardState());

  readonly state = this.stateSignal.asReadonly();

  readonly visibleSteps = computed(() => this.computeVisibleSteps(this.stateSignal()));

  initialize(options: { orderType?: OrderType; skipOrderType?: boolean } = {}): void {
    const skipOrderType = options.skipOrderType ?? !!options.orderType;
    this.stateSignal.set(
      createInitialWizardState({
        orderType: options.orderType,
        skipOrderTypeStep: skipOrderType,
        currentStep: skipOrderType ? WizardStepId.CURRENCY : WizardStepId.ORDER_TYPE,
      }),
    );
  }

  applyContractShortcut(
    currency: string,
    noticePeriod: NoticePeriod,
    sourceContractNumber?: string,
    contractInstitutionCode?: string,
    contractCounterparty?: string,
  ): void {
    this.stateSignal.set(
      createInitialWizardState({
        contractShortcut: true,
        orderType: 'ON_CALL',
        currency,
        noticePeriod,
        sourceContractNumber,
        contractInstitutionCode,
        contractCounterparty,
        skipOrderTypeStep: true,
        currentStep: WizardStepId.OPERATION,
      }),
    );
  }

  reset(): void {
    this.stateSignal.set(createInitialWizardState());
  }

  setOrderType(orderType: OrderType): void {
    this.patch({ orderType });
  }

  setCurrency(currency: string): void {
    this.patch({ currency }, WizardStepId.CURRENCY);
  }

  setOperation(operation: OrderOperation, minAmount?: number): void {
    this.patch({ operation, operationMinAmount: minAmount }, WizardStepId.OPERATION);
  }

  setTenor(tenor: Tenor): void {
    this.patch({ tenor }, WizardStepId.TENOR_OR_NOTICE_PERIOD);
  }

  setNoticePeriod(noticePeriod: NoticePeriod): void {
    this.patch({ noticePeriod }, WizardStepId.TENOR_OR_NOTICE_PERIOD);
  }

  setCounterparty(
    institutionCode: string,
    counterparty: string,
    rate?: number,
    rateDate?: string,
  ): void {
    this.patch({ institutionCode, counterparty, counterpartyRate: rate, counterpartyRateDate: rateDate }, WizardStepId.COUNTERPARTY);
  }

  setValueDate(valueDate: string): void {
    this.patch({ valueDate }, WizardStepId.VALUE_DATE);
  }

  setOrderDetails(amount: number, valueDate?: string, minimumRate?: number): void {
    const state = this.stateSignal();
    const resolvedValueDate =
      state.orderType === 'ON_CALL' ? state.valueDate : valueDate;
    this.patch({ amount, valueDate: resolvedValueDate, minimumRate }, WizardStepId.ORDER_DETAILS);
  }

  completeAndAdvance(): void {
    const state = this.stateSignal();
    const step = state.currentStep;
    if (!this.isStepComplete(step, state)) {
      throw new Error(`Cannot complete step ${step}: required selections missing`);
    }

    const completedSteps = state.completedSteps.includes(step)
      ? state.completedSteps
      : [...state.completedSteps, step];
    const nextStep = this.nextVisibleStep(step, this.computeVisibleSteps(state));
    if (!nextStep) {
      this.stateSignal.set({ ...state, completedSteps });
      return;
    }

    this.stateSignal.set({ ...state, completedSteps, currentStep: nextStep });
  }

  next(): void {
    const state = this.stateSignal();
    const nextStep = this.nextVisibleStep(state.currentStep, this.visibleSteps());
    if (!nextStep) {
      return;
    }
    this.stateSignal.set({ ...state, currentStep: nextStep });
  }

  back(): void {
    const state = this.stateSignal();
    const previousStep = this.previousVisibleStep(state.currentStep, this.visibleSteps());
    if (!previousStep) {
      return;
    }
    this.stateSignal.set({ ...state, currentStep: previousStep });
  }

  goTo(stepId: WizardStepId): void {
    const state = this.stateSignal();
    const visible = this.visibleSteps();
    if (!visible.includes(stepId)) {
      throw new Error(`Step ${stepId} is not visible in the current flow`);
    }
    if (!state.completedSteps.includes(stepId) && stepId !== state.currentStep) {
      throw new Error(`Step ${stepId} is not completed`);
    }
    this.stateSignal.set({ ...state, currentStep: stepId });
  }

  isStepComplete(stepId: WizardStepId, state: WizardState = this.stateSignal()): boolean {
    switch (stepId) {
      case WizardStepId.ORDER_TYPE:
        return !!state.orderType;
      case WizardStepId.CURRENCY:
        return !!state.currency;
      case WizardStepId.OPERATION:
        return !!state.operation;
      case WizardStepId.TENOR_OR_NOTICE_PERIOD:
        return state.orderType === 'TERM' ? !!state.tenor : !!state.noticePeriod;
      case WizardStepId.VALUE_DATE:
        return !!state.valueDate;
      case WizardStepId.COUNTERPARTY:
        if (
          state.contractShortcut &&
          (state.operation === 'DECREASE' || state.operation === 'REDEMPTION')
        ) {
          return !!state.institutionCode && !!state.counterparty;
        }
        return !!state.institutionCode && !!state.counterparty;
      case WizardStepId.ORDER_DETAILS:
        return state.orderType === 'ON_CALL'
          ? state.amount != null
          : state.amount != null && !!state.valueDate;
      case WizardStepId.REVIEW:
        return true;
      default:
        return false;
    }
  }

  private patch(partial: Partial<WizardState>, fromStep?: WizardStepId): void {
    const state = this.stateSignal();
    const nextState = { ...state, ...partial };
    if (fromStep != null) {
      this.clearDownstream(nextState, fromStep);
    }
    this.stateSignal.set(nextState);
  }

  private clearDownstream(state: WizardState, fromStep: WizardStepId): void {
    const visible = this.computeVisibleSteps(state);
    const fromIndex = visible.indexOf(fromStep);
    if (fromIndex < 0) {
      return;
    }

    const downstream = new Set(visible.slice(fromIndex + 1));
    if (downstream.has(WizardStepId.OPERATION)) {
      state.operation = undefined;
      state.operationMinAmount = undefined;
    }
    if (downstream.has(WizardStepId.TENOR_OR_NOTICE_PERIOD)) {
      state.tenor = undefined;
      state.noticePeriod = state.contractShortcut ? state.noticePeriod : undefined;
    }
    if (downstream.has(WizardStepId.VALUE_DATE)) {
      state.valueDate = undefined;
    }
    if (downstream.has(WizardStepId.COUNTERPARTY)) {
      state.institutionCode = undefined;
      state.counterparty = undefined;
      state.counterpartyRate = undefined;
      state.counterpartyRateDate = undefined;
      if (!state.contractShortcut) {
        state.contractInstitutionCode = undefined;
        state.contractCounterparty = undefined;
      }
    }
    if (downstream.has(WizardStepId.ORDER_DETAILS)) {
      state.amount = undefined;
      if (downstream.has(WizardStepId.VALUE_DATE)) {
        state.valueDate = undefined;
      }
      state.minimumRate = undefined;
      state.sourceContractNumber = state.contractShortcut ? state.sourceContractNumber : undefined;
    }

    state.completedSteps = state.completedSteps.filter((step) => !downstream.has(step));
  }

  private computeVisibleSteps(state: WizardState): WizardStepId[] {
    let steps: WizardStepId[];
    if (state.contractShortcut) {
      steps = [...CONTRACT_SHORTCUT_STEPS];
    } else if (state.skipOrderTypeStep) {
      steps = FULL_FLOW_STEPS.filter((step) => step !== WizardStepId.ORDER_TYPE);
    } else {
      steps = [...FULL_FLOW_STEPS];
    }

    if (state.orderType !== 'ON_CALL') {
      steps = steps.filter((step) => step !== WizardStepId.VALUE_DATE);
    }

    return steps;
  }

  private nextVisibleStep(current: WizardStepId, visible: WizardStepId[]): WizardStepId | null {
    const index = visible.indexOf(current);
    return index >= 0 && index < visible.length - 1 ? visible[index + 1] : null;
  }

  private previousVisibleStep(current: WizardStepId, visible: WizardStepId[]): WizardStepId | null {
    const index = visible.indexOf(current);
    return index > 0 ? visible[index - 1] : null;
  }
}
