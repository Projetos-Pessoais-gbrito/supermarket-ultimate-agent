import type { ReceiptItem } from '../api/types';

export type GroupedItem = {
  key: string;
  /** How many receipt lines were merged */
  count: number;
  item: ReceiptItem;
  quantity: number;
  totalPrice: number;
};

/**
 * Merges lines of the same product at the same price (stores often print each unit on its own
 * line: "FLOCAO 400G" twice). Display only; order follows the first occurrence.
 */
export function groupReceiptItems(items: ReceiptItem[]): GroupedItem[] {
  const groups = new Map<string, GroupedItem>();
  for (const item of items) {
    const key = `${item.code}|${item.unit}|${item.unitPrice}`;
    const existing = groups.get(key);
    if (existing) {
      existing.count += 1;
      existing.quantity = round(existing.quantity + item.quantity, 4);
      existing.totalPrice = round(existing.totalPrice + item.totalPrice, 2);
    } else {
      groups.set(key, { key, count: 1, item, quantity: item.quantity, totalPrice: item.totalPrice });
    }
  }
  return [...groups.values()];
}

function round(value: number, decimals: number): number {
  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}
