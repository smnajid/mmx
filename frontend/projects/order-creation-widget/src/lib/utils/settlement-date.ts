export function minSettlementDate(from: Date = new Date()): string {
  const date = new Date(from);
  date.setDate(date.getDate() + 2);
  return formatDate(date);
}

export function formatDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function isOnOrAfterMinSettlementDate(valueDate: string, from: Date = new Date()): boolean {
  return valueDate >= minSettlementDate(from);
}
