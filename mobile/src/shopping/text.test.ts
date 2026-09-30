import type { PriceAlert, ShoppingListItem, ShoppingSuggestion } from '../api/shopping';
import {
  alertHeadline,
  formatDaysAgo,
  formatListQuantity,
  sortListItems,
  suggestionBestPrice,
  suggestionRhythm,
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
    expect(alertHeadline(alert)).toBe('15,0% mais barato que o normal (R$ 5,00)');
    expect(alertHeadline({ ...alert, type: 'RISE', changePercent: 20.5 })).toBe(
      '20,5% mais caro que o normal (R$ 5,00)',
    );
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
