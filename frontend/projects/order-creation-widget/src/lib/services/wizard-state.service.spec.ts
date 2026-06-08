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
      WizardStepId.COUNTERPARTY,
      WizardStepId.ORDER_DETAILS,
      WizardStepId.REVIEW,
    ]);
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
});
