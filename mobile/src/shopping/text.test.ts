import type { ListPrice, PriceAlert, ShoppingListItem, ShoppingSuggestion } from '../api/shopping';
import {
  addedItemsMessage,
  alertHeadline,
  formatDaysAgo,
  formatListQuantity,
  formatUnitPrice,
  formatUsualQuantity,
  itemPrice,
  listEstimate,
  savedListSummary,
  sortListItems,
  suggestionBestPrice,
  suggestionRhythm,
  unpricedNote,
  withChecked,
} from './text';

const suggestion: ShoppingSuggestion = {
  productId: 1,
  name: 'ARROZ TIO JOAO 5KG',
  averageIntervalDays: 10,
  daysSinceLastPurchase: 12,
  lastPurchase: '2026-08-21',
  usualQuantity: 2,
  lastPrice: 25,
  bestRecentPrice: 22,
  bestRecentStore: 'Carrefour',
  inList: false,
};

const alert: PriceAlert = {
  productId: 2,
  name: 'LEITE ITALAC 1L',
  type: 'DEAL',
  latestPrice: 4.25,
  usualPrice: 5,
  changePercent: -15,
  store: 'Assaí',
  date: '2026-08-01',
};

const item = (id: number, checked: boolean): ShoppingListItem => ({
  id,
  productId: null,
  name: `Item ${id}`,
  quantity: null,
  checked,
});

describe('formatDaysAgo', () => {
  it('uses friendly words for today and yesterday', () => {
    expect(formatDaysAgo(0)).toBe('hoje');
    expect(formatDaysAgo(1)).toBe('ontem');
    expect(formatDaysAgo(12)).toBe('há 12 dias');
  });
});

describe('suggestion texts', () => {
  it('describes the buying rhythm', () => {
    expect(suggestionRhythm(suggestion)).toBe('Você compra a cada ~10 dias · última compra há 12 dias');
  });

  it('shows the cheapest store only when it beats the last price', () => {
    expect(suggestionBestPrice(suggestion)).toBe('Mais barato: R$ 22,00 em Carrefour');
    expect(suggestionBestPrice({ ...suggestion, bestRecentPrice: 25 })).toBeNull();
  });
});

describe('alertHeadline', () => {
  it('describes deals and rises against the usual price', () => {
    expect(alertHeadline(alert)).toBe('15,0% mais barato que o normal (você costuma pagar R$ 5,00)');
    expect(alertHeadline({ ...alert, type: 'RISE', changePercent: 20.5 })).toBe(
      '20,5% mais caro que o normal (você costuma pagar R$ 5,00)',
    );
  });
});

describe('formatUsualQuantity', () => {
  it('formats whole and fractional quantities without a multiplication sign', () => {
    expect(formatUsualQuantity(2)).toBe('2');
    expect(formatUsualQuantity(1.5)).toBe('1,5');
  });
});

describe('formatListQuantity', () => {
  it('formats whole and fractional quantities', () => {
    expect(formatListQuantity(2)).toBe('2×');
    expect(formatListQuantity(1.5)).toBe('1,5×');
    expect(formatListQuantity(null)).toBe('');
  });
});

describe('list ordering', () => {
  it('keeps items to buy before checked ones', () => {
    expect(sortListItems([item(1, true), item(2, false), item(3, true), item(4, false)]).map(i => i.id)).toEqual([
      2, 4, 1, 3,
    ]);
  });

  it('moves an item when it is checked', () => {
    const toggled = withChecked([item(1, false), item(2, false)], 1, true);
    expect(toggled.map(i => [i.id, i.checked])).toEqual([
      [2, false],
      [1, true],
    ]);
  });
});

describe('list prices at a market', () => {
  const item = (id: number, productId: number | null, quantity: number | null): ShoppingListItem => ({
    id,
    productId,
    name: `Item ${id}`,
    quantity,
    checked: false,
  });
  const prices: ListPrice[] = [
    { productId: 10, unitPrice: 4.99, unit: 'UN', issuedAt: '2026-09-01T15:00:00Z' },
    { productId: 20, unitPrice: 5.98, unit: 'KG', issuedAt: '2026-09-01T15:00:00Z' },
  ];

  it('multiplies the unit price by the quantity, 1 when none was given', () => {
    expect(itemPrice(item(1, 10, 12), prices)).toEqual({ unitPrice: 4.99, unit: 'UN', total: 59.88 });
    expect(itemPrice(item(2, 10, null), prices)?.total).toBe(4.99);
    expect(itemPrice(item(3, 20, 1.25), prices)?.total).toBe(7.48);
  });

  it('has no price for free text or products never bought there', () => {
    expect(itemPrice(item(1, null, 1), prices)).toBeNull();
    expect(itemPrice(item(2, 30, 1), prices)).toBeNull();
  });

  it('adds up the priced items and counts the others', () => {
    expect(listEstimate([item(1, 10, 2), item(2, 20, 1.25), item(3, null, 1), item(4, 30, 1)], prices)).toEqual({
      total: 17.46,
      unpriced: 2,
    });
    expect(listEstimate([], prices)).toEqual({ total: 0, unpriced: 0 });
  });

  it('formats unit prices and notes', () => {
    expect(formatUnitPrice(5.98, 'KG')).toBe('R$ 5,98/kg');
    expect(unpricedNote(1)).toBe('1 item sem preço nesse mercado');
    expect(unpricedNote(3)).toBe('3 itens sem preço nesse mercado');
  });
});

describe('list messages', () => {
  it('says how many items were added', () => {
    expect(addedItemsMessage({ added: 1, alreadyInList: 0 })).toBe('1 item adicionado');
    expect(addedItemsMessage({ added: 3, alreadyInList: 1 })).toBe('3 itens adicionados · 1 já estava na lista');
    expect(addedItemsMessage({ added: 0, alreadyInList: 2 })).toBe('0 itens adicionados · 2 já estavam na lista');
  });

  it('summarizes a saved list', () => {
    expect(savedListSummary(12, '2026-10-01T15:00:00Z')).toBe('12 itens · salva em 01/10/2026');
    expect(savedListSummary(1, '2026-10-01T15:00:00Z')).toBe('1 item · salva em 01/10/2026');
  });
});
