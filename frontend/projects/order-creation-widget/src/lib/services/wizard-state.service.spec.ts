import { TestBed } from '@angular/core/testing';
import { WizardStepId } from '../models/wizard-state.model';
import { WizardStateService } from './wizard-state.service';

describe('WizardStateService', () => {
  let service: WizardStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [WizardStateService],
    });
    service = TestBed.inject(WizardStateService);
  });

  it('starts at ORDER_TYPE with empty selections in full flow', () => {
    expect(service.state()).toEqual({
      currentStep: WizardStepId.ORDER_TYPE,
      completedSteps: [],
      skipOrderTypeStep: false,
      contractShortcut: false,
    });
    expect(service.visibleSteps()).toEqual([
      WizardStepId.ORDER_TYPE,
      WizardStepId.CURRENCY,
      WizardStepId.OPERATION,
      WizardStepId.TENOR_OR_NOTICE_PERIOD,
      WizardStepId.COUNTERPARTY,
      WizardStepId.ORDER_DETAILS,
      WizardStepId.REVIEW,
    ]);
  });

  it('initialize with orderType skips ORDER_TYPE and starts at CURRENCY', () => {
    service.initialize({ orderType: 'TERM', skipOrderType: true });

    expect(service.state().orderType).toBe('TERM');
    expect(service.state().skipOrderTypeStep).toBe(true);
    expect(service.state().currentStep).toBe(WizardStepId.CURRENCY);
    expect(service.visibleSteps()).not.toContain(WizardStepId.ORDER_TYPE);
  });

  it('setOrderType updates selection', () => {
    service.setOrderType('ON_CALL');
    expect(service.state().orderType).toBe('ON_CALL');
  });

  it('completeAndAdvance marks step complete and moves to next visible step', () => {
    service.setOrderType('TERM');
    service.completeAndAdvance();

    expect(service.state().completedSteps).toContain(WizardStepId.ORDER_TYPE);
    expect(service.state().currentStep).toBe(WizardStepId.CURRENCY);
  });

  it('next and back navigate within visible steps', () => {
    service.setOrderType('TERM');
    service.completeAndAdvance();
    service.setCurrency('EUR');
    service.completeAndAdvance();

    expect(service.state().currentStep).toBe(WizardStepId.OPERATION);

    service.back();
    expect(service.state().currentStep).toBe(WizardStepId.CURRENCY);

    service.next();
    expect(service.state().currentStep).toBe(WizardStepId.OPERATION);
  });

  it('goTo allows navigation only to completed steps', () => {
    service.setOrderType('TERM');
    service.completeAndAdvance();
    service.setCurrency('EUR');
    service.completeAndAdvance();

    service.goTo(WizardStepId.ORDER_TYPE);
    expect(service.state().currentStep).toBe(WizardStepId.ORDER_TYPE);

    expect(() => service.goTo(WizardStepId.OPERATION)).toThrow();
  });

  it('changing an earlier selection clears downstream selections', () => {
    service.setOrderType('TERM');
    service.completeAndAdvance();
    service.setCurrency('EUR');
    service.completeAndAdvance();
    service.setOperation('SUBSCRIPTION');
    service.completeAndAdvance();
    service.setTenor('3M');

    service.goTo(WizardStepId.CURRENCY);
    service.setCurrency('USD');

    expect(service.state().currency).toBe('USD');
    expect(service.state().operation).toBeUndefined();
    expect(service.state().tenor).toBeUndefined();
    expect(service.state().completedSteps).not.toContain(WizardStepId.OPERATION);
  });

  it('applyContractShortcut starts at OPERATION with preset OnCall context', () => {
    service.applyContractShortcut('EUR', '24H');

    expect(service.state()).toMatchObject({
      contractShortcut: true,
      orderType: 'ON_CALL',
      currency: 'EUR',
      noticePeriod: '24H',
      currentStep: WizardStepId.OPERATION,
      skipOrderTypeStep: true,
    });
    expect(service.visibleSteps()).toEqual([
      WizardStepId.OPERATION,
      WizardStepId.VALUE_DATE,
      WizardStepId.COUNTERPARTY,
      WizardStepId.ORDER_DETAILS,
      WizardStepId.REVIEW,
    ]);
  });

  it('preserves sourceContractNumber when changing operation in contract shortcut', () => {
    service.applyContractShortcut('EUR', '24H', 'CT-00042');
    service.setOperation('INCREASE', 50000);
    service.completeAndAdvance();

    service.setOperation('REDEMPTION', 50000);

    expect(service.state().operation).toBe('REDEMPTION');
    expect(service.state().sourceContractNumber).toBe('CT-00042');
  });

  it('setOrderDetails does not modify sourceContractNumber', () => {
    service.applyContractShortcut('EUR', '24H', 'CT-00042');
    service.setOperation('INCREASE', 50000);
    service.completeAndAdvance();
    service.setValueDate('2026-06-10');
    service.completeAndAdvance();
    service.setCounterparty('BNKCO', 'BankCo');
    service.completeAndAdvance();

    service.setOrderDetails(600000);

    expect(service.state().sourceContractNumber).toBe('CT-00042');
    expect(service.state().amount).toBe(600000);
  });

  it('reset restores initial full-flow state', () => {
    service.setOrderType('TERM');
    service.completeAndAdvance();
    service.reset();

    expect(service.state()).toEqual({
      currentStep: WizardStepId.ORDER_TYPE,
      completedSteps: [],
      skipOrderTypeStep: false,
      contractShortcut: false,
    });
  });

  it('OnCall visibleSteps includes VALUE_DATE between notice and counterparty', () => {
    service.setOrderType('ON_CALL');

    const steps = service.visibleSteps();
    const noticeIndex = steps.indexOf(WizardStepId.TENOR_OR_NOTICE_PERIOD);
    const valueDateIndex = steps.indexOf(WizardStepId.VALUE_DATE);
    const counterpartyIndex = steps.indexOf(WizardStepId.COUNTERPARTY);

    expect(valueDateIndex).toBeGreaterThan(noticeIndex);
    expect(counterpartyIndex).toBeGreaterThan(valueDateIndex);
  });

  it('Term visibleSteps omits VALUE_DATE', () => {
    service.setOrderType('TERM');

    expect(service.visibleSteps()).not.toContain(WizardStepId.VALUE_DATE);
  });

  it('contract shortcut visibleSteps are OPERATION → VALUE_DATE → COUNTERPARTY → ORDER_DETAILS → REVIEW', () => {
    service.applyContractShortcut('EUR', '24H');

    expect(service.visibleSteps()).toEqual([
      WizardStepId.OPERATION,
      WizardStepId.VALUE_DATE,
      WizardStepId.COUNTERPARTY,
      WizardStepId.ORDER_DETAILS,
      WizardStepId.REVIEW,
    ]);
  });

  it('isStepComplete(VALUE_DATE) requires valueDate', () => {
    service.setOrderType('ON_CALL');
    expect(service.isStepComplete(WizardStepId.VALUE_DATE)).toBe(false);

    service.setValueDate('2026-06-30');
    expect(service.isStepComplete(WizardStepId.VALUE_DATE)).toBe(true);
  });

  it('setCounterparty does not clear valueDate', () => {
    service.setOrderType('ON_CALL');
    service.setValueDate('2026-06-30');
    service.setCounterparty('BNKCO', 'BankCo', 3.5, '2026-06-07');

    expect(service.state().valueDate).toBe('2026-06-30');
  });

  it('clearDownstream preserves contractInstitutionCode and contractCounterparty in shortcut mode', () => {
    service.applyContractShortcut('EUR', '24H', 'CT-00042', 'BNKCO', 'BankCo');
    service.setOperation('INCREASE', 50000);
    service.completeAndAdvance();
    service.setValueDate('2026-06-10');
    service.completeAndAdvance();
    service.setCounterparty('BNKCO', 'BankCo', 3.5, '2026-06-07');

    service.setOperation('DECREASE', 50000);

    expect(service.state().contractInstitutionCode).toBe('BNKCO');
    expect(service.state().contractCounterparty).toBe('BankCo');
    expect(service.state().institutionCode).toBeUndefined();
    expect(service.state().counterparty).toBeUndefined();
  });

  it('isStepComplete(COUNTERPARTY) is true for outflow shortcut when institution set without rate', () => {
    service.applyContractShortcut('EUR', '24H', 'CT-00042', 'BNKCO', 'BankCo');
    service.setOperation('DECREASE', 50000);
    service.setCounterparty('BNKCO', 'BankCo');

    expect(service.isStepComplete(WizardStepId.COUNTERPARTY)).toBe(true);
    expect(service.state().counterpartyRate).toBeUndefined();
  });

  it('changing notice period clears valueDate and downstream', () => {
    service.setOrderType('ON_CALL');
    service.completeAndAdvance();
    service.setCurrency('EUR');
    service.completeAndAdvance();
    service.setOperation('INCREASE', 500000);
    service.completeAndAdvance();
    service.setNoticePeriod('24H');
    service.completeAndAdvance();
    service.setValueDate('2026-06-30');
    service.completeAndAdvance();
    service.setCounterparty('BNKCO', 'BankCo', 3.5, '2026-06-07');

    service.setNoticePeriod('48H');

    expect(service.state().valueDate).toBeUndefined();
    expect(service.state().institutionCode).toBeUndefined();
    expect(service.state().counterparty).toBeUndefined();
  });
});
