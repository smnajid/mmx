import { describe, expect, it } from 'vitest';
import type { OrderCreationPayload } from 'order-creation-widget';
import {
  generatePlaygroundExternalReference,
  mapOrderCreationPayloadToReceiveRequest,
} from './order-creation-payload.mapper';

describe('mapOrderCreationPayloadToReceiveRequest', () => {
  const termPayload: OrderCreationPayload = {
    legalEntityCode: 'LOC',
    portfolioNumber: 'PF-001',
    orderType: 'TERM',
    currency: 'EUR',
    operation: 'SUBSCRIPTION',
    tenor: '3M',
    institutionCode: 'BNKCO',
    counterparty: 'BankCo',
    amount: 1_000_000,
    valueDate: '2026-06-15',
    minimumRate: 3.25,
  };

  it('maps operation to orderOperation and preserves institutionCode', () => {
    const request = mapOrderCreationPayloadToReceiveRequest(
      termPayload,
      'PLAYGROUND-REF-001',
    );

    expect(request).toEqual({
      legalEntityCode: 'LOC',
      externalOrderReference: 'PLAYGROUND-REF-001',
      orderType: 'TERM',
      orderOperation: 'SUBSCRIPTION',
      portfolioNumber: 'PF-001',
      currency: 'EUR',
      amount: 1_000_000,
      valueDate: '2026-06-15',
      institutionCode: 'BNKCO',
      minimumRate: 3.25,
      tenor: '3M',
    });
    expect(request).not.toHaveProperty('counterparty');
  });

  it('maps OnCall lifecycle fields when present', () => {
    const onCallPayload: OrderCreationPayload = {
      ...termPayload,
      orderType: 'ON_CALL',
      operation: 'INCREASE',
      noticePeriod: '24H',
      sourceContractNumber: 'CT-00042',
      tenor: undefined,
    };

    const request = mapOrderCreationPayloadToReceiveRequest(
      onCallPayload,
      'PLAYGROUND-REF-002',
    );

    expect(request.orderOperation).toBe('INCREASE');
    expect(request.noticePeriod).toBe('24H');
    expect(request.sourceContractNumber).toBe('CT-00042');
    expect(request).not.toHaveProperty('tenor');
  });

  it('omits optional fields when absent', () => {
    const minimal: OrderCreationPayload = {
      legalEntityCode: 'PAR',
      portfolioNumber: 'PF-001',
      orderType: 'ON_CALL',
      currency: 'EUR',
      operation: 'SUBSCRIPTION',
      institutionCode: 'BNKCO',
      counterparty: 'BankCo',
      amount: 500_000,
      valueDate: '2026-06-15',
      noticePeriod: '48H',
    };

    const request = mapOrderCreationPayloadToReceiveRequest(minimal, 'PLAYGROUND-REF-003');

    expect(request.minimumRate).toBeUndefined();
    expect(request.sourceContractNumber).toBeUndefined();
    expect(request.noticePeriod).toBe('48H');
  });
});

describe('generatePlaygroundExternalReference', () => {
  it('uses PLAYGROUND prefix with timestamp and suffix', () => {
    const ref = generatePlaygroundExternalReference(new Date('2026-06-15T14:30:45Z'));
    expect(ref).toMatch(/^PLAYGROUND-20260615143045-[A-Z0-9]{4}$/);
  });
});
