import { describe, expect, it } from 'vitest';
import {
  curvePointKey,
  groupOnCallRateSegmentsForReview,
  OPEN_END_SENTINEL,
} from './group-oncall-rate-segments-for-review';
import type { OnCallRateSegment } from '../../core/api/oncall-rate-settings-api.service';

function segment(
  overrides: Partial<OnCallRateSegment> & Pick<OnCallRateSegment, 'currency' | 'noticePeriod' | 'valueDate'>
): OnCallRateSegment {
  return {
    segmentId: '00000000-0000-0000-0000-000000000001',
    institutionCode: 'HSBC-01',
    rate: 1,
    endDate: OPEN_END_SENTINEL,
    status: 'VALID',
    ...overrides,
  };
}

describe('groupOnCallRateSegmentsForReview', () => {
  it('groups by currency and notice period', () => {
    const tree = groupOnCallRateSegmentsForReview([
      segment({ currency: 'EUR', noticePeriod: '24H', valueDate: '2026-01-01', segmentId: 'a' }),
      segment({ currency: 'USD', noticePeriod: '48H', valueDate: '2026-02-01', segmentId: 'b' }),
      segment({ currency: 'EUR', noticePeriod: '48H', valueDate: '2026-03-01', segmentId: 'c' }),
    ]);

    expect(tree.map((n) => curvePointKey(n.currency, n.noticePeriod))).toEqual([
      curvePointKey('EUR', '24H'),
      curvePointKey('EUR', '48H'),
      curvePointKey('USD', '48H'),
    ]);
  });

  it('filters canceled segments and omits curve points that are only canceled', () => {
    const tree = groupOnCallRateSegmentsForReview([
      segment({
        currency: 'EUR',
        noticePeriod: '24H',
        valueDate: '2026-01-01',
        status: 'CANCELED',
        segmentId: 'canceled-only',
      }),
      segment({
        currency: 'USD',
        noticePeriod: '24H',
        valueDate: '2026-02-01',
        status: 'VALID',
        segmentId: 'visible',
      }),
      segment({
        currency: 'USD',
        noticePeriod: '24H',
        valueDate: '2026-01-01',
        status: 'CANCELED',
        segmentId: 'canceled-mix',
      }),
    ]);

    expect(tree).toHaveLength(1);
    expect(tree[0].currency).toBe('USD');
    expect(tree[0].segments).toHaveLength(1);
    expect(tree[0].segments[0].segmentId).toBe('visible');
  });

  it('sorts curve points by currency then notice catalog order', () => {
    const tree = groupOnCallRateSegmentsForReview([
      segment({ currency: 'USD', noticePeriod: '48H', valueDate: '2026-01-01' }),
      segment({ currency: 'EUR', noticePeriod: '48H', valueDate: '2026-01-01' }),
      segment({ currency: 'EUR', noticePeriod: '24H', valueDate: '2026-01-01' }),
    ]);

    expect(tree.map((n) => `${n.currency}:${n.noticePeriod}`)).toEqual([
      'EUR:24H',
      'EUR:48H',
      'USD:48H',
    ]);
  });

  it('orders segments by value date descending within a curve point', () => {
    const tree = groupOnCallRateSegmentsForReview([
      segment({ currency: 'EUR', noticePeriod: '24H', valueDate: '2026-01-01', segmentId: 'old' }),
      segment({ currency: 'EUR', noticePeriod: '24H', valueDate: '2026-06-01', segmentId: 'new' }),
    ]);

    expect(tree[0].segments.map((s) => s.segmentId)).toEqual(['new', 'old']);
  });

  it('derives currentSegment from open end date sentinel', () => {
    const tree = groupOnCallRateSegmentsForReview([
      segment({
        currency: 'EUR',
        noticePeriod: '24H',
        valueDate: '2026-01-01',
        endDate: '2026-05-30',
        segmentId: 'closed',
      }),
      segment({
        currency: 'EUR',
        noticePeriod: '24H',
        valueDate: '2026-06-01',
        endDate: OPEN_END_SENTINEL,
        rate: 3.25,
        status: 'PENDING_CONFIRMATION',
        segmentId: 'open',
      }),
    ]);

    expect(tree[0].currentSegment?.segmentId).toBe('open');
    expect(tree[0].currentSegment?.rate).toBe(3.25);
  });
});
