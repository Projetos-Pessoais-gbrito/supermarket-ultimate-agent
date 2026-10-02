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

/** A product the user bought, offered while they type an item. */
export type ProductOption = { productId: number; name: string };

/** A market (chain) the user has receipts from. */
export type Market = { name: string; lastPurchase: string };

/** Unit price of a listed product on the user's latest receipt from the market. */
export type ListPrice = { productId: number; unitPrice: number; unit: string; issuedAt: string };

export type ListPrices = { market: string; items: ListPrice[] };

/** alreadyInList: items skipped because they were already on the list to buy */
export type AddedItems = { added: number; alreadyInList: number };

export type SavedShoppingList = { id: number; name: string; itemCount: number; updatedAt: string };

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
  addFromReceipt: (token: string, receiptId: number, lineNumbers: number[]) =>
    apiRequest<AddedItems>('/api/shopping-list/from-receipt', {
      method: 'POST',
      body: { receiptId, lineNumbers },
      token,
    }),
  searchProducts: (token: string, text: string) =>
    apiRequest<ProductOption[]>(`/api/shopping-list/products?q=${encodeURIComponent(text)}`, { token }),
  markets: (token: string) => apiRequest<Market[]>('/api/shopping-list/markets', { token }),
  prices: (token: string, market: string) =>
    apiRequest<ListPrices>(`/api/shopping-list/prices?market=${encodeURIComponent(market)}`, { token }),
  savedLists: (token: string) => apiRequest<SavedShoppingList[]>('/api/shopping-list/saved', { token }),
  saveList: (token: string, name: string) =>
    apiRequest<SavedShoppingList>('/api/shopping-list/saved', { method: 'POST', body: { name }, token }),
  applySavedList: (token: string, id: number) =>
    apiRequest<AddedItems>(`/api/shopping-list/saved/${id}/apply`, { method: 'POST', token }),
  removeSavedList: (token: string, id: number) =>
    apiRequest<void>(`/api/shopping-list/saved/${id}`, { method: 'DELETE', token }),
};
