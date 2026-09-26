import { formatAmountForInput, parseAmountInput } from './parse-amount-input';

describe('parseAmountInput', () => {
  it('parses plain integers', () => {
    expect(parseAmountInput('1000000')).toBe(1_000_000);
    expect(parseAmountInput('500')).toBe(500);
  });

  it('parses comma-separated amounts', () => {
    expect(parseAmountInput('1,000,000')).toBe(1_000_000);
  });

  it('parses thousands suffix', () => {
    expect(parseAmountInput('500k')).toBe(500_000);
    expect(parseAmountInput('1.5K')).toBe(1_500);
  });

  it('parses millions suffix', () => {
    expect(parseAmountInput('1m')).toBe(1_000_000);
    expect(parseAmountInput('2.5M')).toBe(2_500_000);
  });

  it('parses billions suffix', () => {
    expect(parseAmountInput('1b')).toBe(1_000_000_000);
    expect(parseAmountInput('1.2B')).toBe(1_200_000_000);
  });

  it('returns null for empty or invalid input', () => {
    expect(parseAmountInput('')).toBeNull();
    expect(parseAmountInput('   ')).toBeNull();
    expect(parseAmountInput('abc')).toBeNull();
    expect(parseAmountInput('1x')).toBeNull();
  });
});

describe('formatAmountForInput', () => {
  it('formats whole numbers with grouping', () => {
    expect(formatAmountForInput(1_500_000)).toBe('1,500,000');
  });
});
