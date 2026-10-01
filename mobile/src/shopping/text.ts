import type { PriceAlert, ShoppingListItem, ShoppingSuggestion } from '../api/shopping';
import { formatCurrency, formatPercent } from '../format';

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
