import { describe, expect, it } from 'vitest';
import { groupTermRatesForReview } from './group-term-rates-for-review';
import type { TermRate } from '../../core/api/term-rate-settings-api.service';

function row(
  institutionCode: string,
  currency: string,
  tenor: TermRate['tenor'],
  rate: number
): TermRate {
  return {
    tradingDate: '2026-06-01',
    institutionCode,
    currency,
    tenor,
    rate,
    uploadedAt: '2026-06-01T08:00:00Z',
    uploadedBy: 'trader-test',
  };
}

describe('groupTermRatesForReview', () => {
  it('groups by institution then currency with catalog tenor order', () => {
    const tree = groupTermRatesForReview([
      row('BP-01', 'EUR', '3M', 1.1),
      row('BI-01', 'USD', '1M', 2),
      row('BI-01', 'USD', '3M', 3),
      row('BI-01', 'USD', '1W', 4),
      row('BP-01', 'EUR', '1W', 5),
    ]);

    expect(tree.map((n) => n.institutionCode)).toEqual(['BI-01', 'BP-01']);
    expect(tree[0].currencies.map((c) => c.currency)).toEqual(['USD']);
    expect(tree[0].currencies[0].tenors.map((t) => t.tenor)).toEqual(['1W', '1M', '3M']);
    expect(tree[1].currencies[0].tenors.map((t) => t.tenor)).toEqual(['1W', '3M']);
  });

  it('sorts currencies ascending within an institution', () => {
    const tree = groupTermRatesForReview([
      row('BI-01', 'USD', '1W', 1),
      row('BI-01', 'CHF', '1W', 2),
      row('BI-01', 'EUR', '1W', 3),
    ]);

    expect(tree[0].currencies.map((c) => c.currency)).toEqual(['CHF', 'EUR', 'USD']);
  });

  it('counts rates per institution', () => {
    const tree = groupTermRatesForReview([
      row('BI-01', 'USD', '1W', 1),
      row('BI-01', 'USD', '1M', 2),
      row('BI-01', 'EUR', '1W', 3),
    ]);

    expect(tree[0].rateCount).toBe(3);
  });
});
