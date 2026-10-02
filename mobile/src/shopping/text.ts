import type { AddedItems, ListPrice, PriceAlert, ShoppingListItem, ShoppingSuggestion } from '../api/shopping';
import { formatCurrency, formatDate, formatPercent } from '../format';

/** 0 → "hoje", 1 → "ontem", 12 → "há 12 dias" */
export function formatDaysAgo(days: number): string {
  if (days <= 0) {
    return 'hoje';
  }
  return days === 1 ? 'ontem' : `há ${days} dias`;
}

/** "Você compra a cada ~7 dias · última compra há 9 dias" */
export function suggestionRhythm(suggestion: ShoppingSuggestion): string {
  const every = suggestion.averageIntervalDays === 1 ? 'todo dia' : `a cada ~${suggestion.averageIntervalDays} dias`;
  return `Você compra ${every} · última compra ${formatDaysAgo(suggestion.daysSinceLastPurchase)}`;
}

/** Where it was cheapest, only when that beats the last price paid. */
export function suggestionBestPrice(suggestion: ShoppingSuggestion): string | null {
  if (suggestion.bestRecentPrice >= suggestion.lastPrice) {
    return null;
  }
  return `Mais barato: ${formatCurrency(suggestion.bestRecentPrice)} em ${suggestion.bestRecentStore}`;
}

/** "12,3% mais barato que o normal (você costuma pagar R$ 5,00)" */
export function alertHeadline(alert: PriceAlert): string {
  const direction = alert.type === 'DEAL' ? 'mais barato' : 'mais caro';
  return `${formatPercent(alert.changePercent)} ${direction} que o normal (você costuma pagar ${formatCurrency(alert.usualPrice)})`;
}

/** 2 → "2", 1.5 → "1,5" (how much is usually bought at a time) */
export function formatUsualQuantity(quantity: number): string {
  return String(quantity).replace('.', ',');
}

/** 2 → "2×", 1.5 → "1,5×"; empty when no quantity was given. */
export function formatListQuantity(quantity: number | null): string {
  if (quantity === null || quantity === undefined) {
    return '';
  }
  return Number.isInteger(quantity) ? `${quantity}×` : `${String(quantity).replace('.', ',')}×`;
}

/** Items still to buy first; checked ones keep their order at the end. */
export function sortListItems(items: ShoppingListItem[]): ShoppingListItem[] {
  return [...items.filter(item => !item.checked), ...items.filter(item => item.checked)];
}

/** Optimistic toggle used while the request is in flight. */
export function withChecked(items: ShoppingListItem[], id: number, checked: boolean): ShoppingListItem[] {
  return sortListItems(items.map(item => (item.id === id ? { ...item, checked } : item)));
}

/** What a list item costs at the chosen market: unit price × quantity (1 when none was given). */
export type ItemPrice = { unitPrice: number; unit: string; total: number };

export function itemPrice(item: ShoppingListItem, prices: ListPrice[]): ItemPrice | null {
  const price = item.productId === null ? undefined : prices.find(p => p.productId === item.productId);
  if (!price) {
    return null;
  }
  const total = Math.round(price.unitPrice * (item.quantity ?? 1) * 100) / 100;
  return { unitPrice: price.unitPrice, unit: price.unit, total };
}

/** Estimated cost of the whole list at the market; items without a price there are counted apart. */
export function listEstimate(items: ShoppingListItem[], prices: ListPrice[]): { total: number; unpriced: number } {
  let total = 0;
  let unpriced = 0;
  for (const item of items) {
    const price = itemPrice(item, prices);
    if (price) {
      total += price.total;
    } else {
      unpriced++;
    }
  }
  return { total: Math.round(total * 100) / 100, unpriced };
}

/** 5.98 "KG" → "R$ 5,98/kg" */
export function formatUnitPrice(unitPrice: number, unit: string): string {
  return `${formatCurrency(unitPrice)}/${unit.toLowerCase()}`;
}

/** 1 → "1 item sem preço nesse mercado" */
export function unpricedNote(unpriced: number): string {
  return unpriced === 1 ? '1 item sem preço nesse mercado' : `${unpriced} itens sem preço nesse mercado`;
}

/** "3 itens adicionados · 1 já estava na lista" */
export function addedItemsMessage({ added, alreadyInList }: AddedItems): string {
  const addedText = added === 1 ? '1 item adicionado' : `${added} itens adicionados`;
  if (alreadyInList === 0) {
    return addedText;
  }
  return `${addedText} · ${alreadyInList === 1 ? '1 já estava' : `${alreadyInList} já estavam`} na lista`;
}

/** "12 itens · salva em 01/10/2026" */
export function savedListSummary(itemCount: number, updatedAt: string): string {
  return `${itemCount === 1 ? '1 item' : `${itemCount} itens`} · salva em ${formatDate(updatedAt)}`;
}
