import type {
  OnCallNoticePeriod,
  OnCallRateSegment,
} from '../../core/api/oncall-rate-settings-api.service';

export const OPEN_END_SENTINEL = '2999-12-31';

export const NOTICE_CATALOG_ORDER: readonly OnCallNoticePeriod[] = ['24H', '48H'];

const NOTICE_RANK = new Map<string, number>(NOTICE_CATALOG_ORDER.map((np, i) => [np, i]));

export interface OnCallCurvePointNode {
  currency: string;
  noticePeriod: OnCallNoticePeriod;
  segments: OnCallRateSegment[];
  currentSegment?: OnCallRateSegment;
}

export function curvePointKey(currency: string, noticePeriod: OnCallNoticePeriod): string {
  return `${currency}\0${noticePeriod}`;
}

export function formatOnCallEndDate(endDate: string): string {
  return endDate === OPEN_END_SENTINEL ? 'Open' : endDate;
}

export function groupOnCallRateSegmentsForReview(segments: OnCallRateSegment[]): OnCallCurvePointNode[] {
  const visible = segments.filter((s) => s.status !== 'CANCELED');
  const byCurvePoint = new Map<string, OnCallRateSegment[]>();

  for (const row of visible) {
    const key = curvePointKey(row.currency, row.noticePeriod);
    const list = byCurvePoint.get(key);
    if (list) {
      list.push(row);
    } else {
      byCurvePoint.set(key, [row]);
    }
  }

  const keys = [...byCurvePoint.keys()].sort((a, b) => {
    const [currencyA, noticeA] = a.split('\0');
    const [currencyB, noticeB] = b.split('\0');
    const currencyCmp = currencyA.localeCompare(currencyB);
    if (currencyCmp !== 0) {
      return currencyCmp;
    }
    return compareNoticePeriods(noticeA, noticeB);
  });

  return keys.map((key) => {
    const [currency, noticePeriod] = key.split('\0') as [string, OnCallNoticePeriod];
    const pointSegments = [...byCurvePoint.get(key)!].sort((a, b) =>
      b.valueDate.localeCompare(a.valueDate)
    );
    const currentSegment = pointSegments.find((s) => s.endDate === OPEN_END_SENTINEL);
    return {
      currency,
      noticePeriod,
      segments: pointSegments,
      currentSegment,
    };
  });
}

function compareNoticePeriods(a: string, b: string): number {
  const ra = NOTICE_RANK.get(a) ?? Number.MAX_SAFE_INTEGER;
  const rb = NOTICE_RANK.get(b) ?? Number.MAX_SAFE_INTEGER;
  if (ra !== rb) {
    return ra - rb;
  }
  return a.localeCompare(b);
}
