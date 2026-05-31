import type { TenorCode, TermRate } from '../../core/api/term-rate-settings-api.service';

export const TENOR_CATALOG_ORDER: readonly TenorCode[] = ['1W', '2W', '1M', '3M', '6M', '1Y'];

const TENOR_RANK = new Map<string, number>(TENOR_CATALOG_ORDER.map((t, i) => [t, i]));

export interface ReviewTenorLeaf {
  tenor: TenorCode;
  rate: number;
}

export interface ReviewCurrencyNode {
  currency: string;
  tenors: ReviewTenorLeaf[];
}

export interface ReviewInstitutionNode {
  institutionCode: string;
  currencies: ReviewCurrencyNode[];
  rateCount: number;
}

export function groupTermRatesForReview(rates: TermRate[]): ReviewInstitutionNode[] {
  const byInstitution = new Map<string, Map<string, ReviewTenorLeaf[]>>();

  for (const row of rates) {
    let byCurrency = byInstitution.get(row.institutionCode);
    if (!byCurrency) {
      byCurrency = new Map();
      byInstitution.set(row.institutionCode, byCurrency);
    }
    let tenors = byCurrency.get(row.currency);
    if (!tenors) {
      tenors = [];
      byCurrency.set(row.currency, tenors);
    }
    tenors.push({ tenor: row.tenor, rate: row.rate });
  }

  const institutions = [...byInstitution.keys()].sort((a, b) => a.localeCompare(b));

  return institutions.map((institutionCode) => {
    const byCurrency = byInstitution.get(institutionCode)!;
    const currencies = [...byCurrency.keys()].sort((a, b) => a.localeCompare(b));

    let rateCount = 0;
    const currencyNodes: ReviewCurrencyNode[] = currencies.map((currency) => {
      const leaves = byCurrency.get(currency)!;
      leaves.sort((a, b) => compareTenors(a.tenor, b.tenor));
      rateCount += leaves.length;
      return { currency, tenors: leaves };
    });

    return { institutionCode, currencies: currencyNodes, rateCount };
  });
}

export function reviewCurrencyKey(institutionCode: string, currency: string): string {
  return `${institutionCode}\0${currency}`;
}

function compareTenors(a: string, b: string): number {
  const ra = TENOR_RANK.get(a) ?? Number.MAX_SAFE_INTEGER;
  const rb = TENOR_RANK.get(b) ?? Number.MAX_SAFE_INTEGER;
  if (ra !== rb) {
    return ra - rb;
  }
  return a.localeCompare(b);
}
