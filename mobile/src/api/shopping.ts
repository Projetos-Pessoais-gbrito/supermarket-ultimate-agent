import { apiRequest } from './client';

/** A regularly bought product whose usual interval has passed (GET /api/insights/shopping-list). */
export type ShoppingSuggestion = {
  productId: number;
  name: string;
  averageIntervalDays: number;
  daysSinceLastPurchase: number;
  lastPurchase: string;
  usualQuantity: number;
  lastPrice: number;
  bestRecentPrice: number;
  bestRecentStore: string;
  inList: boolean;
};

export type PriceAlert = {
  productId: number;
  name: string;
  type: 'DEAL' | 'RISE';
  latestPrice: number;
  usualPrice: number;
  /** Negative for a deal */
  changePercent: number;
  store: string;
  date: string;
};

export type ShoppingListItem = {
  id: number;
  /** null for free-text items */
  productId: number | null;
  name: string;
  quantity: number | null;
  checked: boolean;
};

export type NewShoppingListItem = { productId: number; quantity?: number } | { name: string; quantity?: number };

export const shoppingApi = {
  suggestions: (token: string) =>
    apiRequest<{ items: ShoppingSuggestion[] }>('/api/insights/shopping-list', { token }),
  priceAlerts: (token: string) => apiRequest<{ items: PriceAlert[] }>('/api/insights/price-alerts', { token }),
  list: (token: string) => apiRequest<ShoppingListItem[]>('/api/shopping-list', { token }),
  add: (token: string, item: NewShoppingListItem) =>
    apiRequest<ShoppingListItem>('/api/shopping-list', { method: 'POST', body: item, token }),
  setChecked: (token: string, id: number, checked: boolean) =>
    apiRequest<ShoppingListItem>(`/api/shopping-list/${id}`, { method: 'PATCH', body: { checked }, token }),
  remove: (token: string, id: number) => apiRequest<void>(`/api/shopping-list/${id}`, { method: 'DELETE', token }),
  removeChecked: (token: string) => apiRequest<void>('/api/shopping-list/checked', { method: 'DELETE', token }),
};
