const AMOUNT_SUFFIX_MULTIPLIERS: Record<string, number> = {
  k: 1_000,
  m: 1_000_000,
  b: 1_000_000_000,
};

/**
 * Parses monetary amount text, including K/M/B suffix shortcuts and comma separators.
 * Returns null when the input is empty or not a valid amount.
 */
export function parseAmountInput(raw: string): number | null {
  const normalized = raw.trim().replace(/,/g, '').replace(/\s+/g, '');
  if (!normalized) {
    return null;
  }

  const match = normalized.match(/^([+-]?\d*\.?\d+)([kmb])?$/i);
  if (!match) {
    return null;
  }

  const base = Number(match[1]);
  if (!Number.isFinite(base)) {
    return null;
  }

  const suffix = match[2]?.toLowerCase();
  if (!suffix) {
    return base;
  }

  return base * AMOUNT_SUFFIX_MULTIPLIERS[suffix];
}

/** Formats a parsed amount for display in an amount input after blur. */
export function formatAmountForInput(value: number): string {
  if (!Number.isFinite(value)) {
    return '';
  }
  if (Number.isInteger(value)) {
    return value.toLocaleString('en-US', { maximumFractionDigits: 0 });
  }
  return String(value);
}
