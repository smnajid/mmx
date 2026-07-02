import { formatAmountForInput, parseAmountInput } from './parse-amount-input';

describe('parseAmountInput', () => {
  it('parses plain integers', () => {
    expect(parseAmountInput('1000000')).toBe(1_000_000);
  });

  it('parses millions suffix', () => {
    expect(parseAmountInput('1.5M')).toBe(1_500_000);
  });

  it('returns null for invalid input', () => {
    expect(parseAmountInput('abc')).toBeNull();
  });
});

describe('formatAmountForInput', () => {
  it('formats whole numbers with grouping', () => {
    expect(formatAmountForInput(1_500_000)).toBe('1,500,000');
  });
});
