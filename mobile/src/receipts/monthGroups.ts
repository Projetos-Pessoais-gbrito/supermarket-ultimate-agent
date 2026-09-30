import type { MonthlyTotal, ReceiptSummary } from '../api/types';
import { formatMonthLong } from '../format';

// Brazil has had no daylight saving time since 2019, so São Paulo is always UTC-3.
const SAO_PAULO_OFFSET_MS = -3 * 60 * 60 * 1000;

/** "2026-10-01T01:30:00Z" → "2026-09" (the São Paulo month) */
export function saoPauloMonth(iso: string): string {
  return new Date(new Date(iso).getTime() + SAO_PAULO_OFFSET_MS).toISOString().slice(0, 7);
}

/** "2026-09" → "Setembro de 2026" */
export function monthTitle(yearMonth: string): string {
  const name = formatMonthLong(yearMonth);
  return `${name.charAt(0).toUpperCase()}${name.slice(1)} de ${yearMonth.slice(0, 4)}`;
}

/** "2026-09" → "set/2026", for the filter chips */
export function monthChipLabel(yearMonth: string): string {
  return `${formatMonthLong(yearMonth).slice(0, 3)}/${yearMonth.slice(0, 4)}`;
}

export type ReceiptListRow =
  | { type: 'month'; key: string; month: string; total: number | null; receiptCount: number | null }
  | { type: 'receipt'; key: string; receipt: ReceiptSummary };

/**
 * Newest-first receipts with a header before each month. Totals come from the server, so they
 * cover the whole month even while only its first receipts are loaded.
 */
export function groupByMonth(receipts: ReceiptSummary[], totals: MonthlyTotal[]): ReceiptListRow[] {
  const totalsByMonth = new Map(totals.map(total => [total.month, total]));
  const rows: ReceiptListRow[] = [];
  let current: string | null = null;
  for (const receipt of receipts) {
    const month = saoPauloMonth(receipt.issuedAt);
    if (month !== current) {
      const total = totalsByMonth.get(month);
      rows.push({
        type: 'month',
        key: `month-${month}`,
        month,
        total: total?.total ?? null,
        receiptCount: total?.receiptCount ?? null,
      });
      current = month;
    }
    rows.push({ type: 'receipt', key: `receipt-${receipt.id}`, receipt });
  }
  return rows;
}
