import { apiRequest, apiRequestWithStatus, IMPORT_TIMEOUT_MS } from './client';
import type {
  BestDayInsight,
  BudgetCategoryOption,
  BudgetSettings,
  BudgetStatus,
  CategoryProducts,
  InflationInsight,
  InsightSummary,
  PriceHistory,
  Me,
  ReceiptDetails,
  ReceiptFilters,
  ReceiptListPage,
  SavingsInsight,
  SpendingInsight,
  TokenResponse,
} from './types';

type Credentials = { email: string; password: string };

export const authApi = {
  register: (credentials: Credentials) =>
    apiRequest<TokenResponse>('/api/auth/register', { method: 'POST', body: credentials }),
  login: (credentials: Credentials) =>
    apiRequest<TokenResponse>('/api/auth/login', { method: 'POST', body: credentials }),
  refresh: (refreshToken: string) =>
    apiRequest<TokenResponse>('/api/auth/refresh', { method: 'POST', body: { refreshToken } }),
  logout: (refreshToken: string) => apiRequest<void>('/api/auth/logout', { method: 'POST', body: { refreshToken } }),
  me: (token: string) => apiRequest<Me>('/api/me', { token }),
};

export const accountApi = {
  /** Everything the app keeps about the user (LGPD), as returned by the backend. */
  export: (token: string) => apiRequest<unknown>('/api/me/export', { token }),
  delete: (token: string, password: string) =>
    apiRequest<void>('/api/me', { method: 'DELETE', body: { password }, token }),
};

/** 201 = imported now; 200 = the user already had this receipt. */
export type ImportResult = { receipt: ReceiptDetails; alreadyImported: boolean };

const toImportResult = ({ data, status }: { data: ReceiptDetails; status: number }): ImportResult => ({
  receipt: data,
  alreadyImported: status === 200,
});

export const receiptsApi = {
  import: (token: string, qrCodeUrl: string) =>
    apiRequestWithStatus<ReceiptDetails>('/api/receipts', {
      method: 'POST',
      body: { qrCodeUrl },
      token,
      timeoutMs: IMPORT_TIMEOUT_MS,
    }).then(toImportResult),
  /** SEFAZ page the user opened in the app after solving the captcha (key-only links). */
  importPage: (token: string, accessKey: string, html: string) =>
    apiRequestWithStatus<ReceiptDetails>('/api/receipts/page', {
      method: 'POST',
      body: { accessKey, html },
      token,
      timeoutMs: IMPORT_TIMEOUT_MS,
    }).then(toImportResult),
  list: (token: string, page = 0, size = 20, filters: ReceiptFilters = {}) =>
    apiRequest<ReceiptListPage>(`/api/receipts?${receiptListQuery(page, size, filters)}`, { token }),
  details: (token: string, id: number) => apiRequest<ReceiptDetails>(`/api/receipts/${id}`, { token }),
  remove: (token: string, id: number) => apiRequest<void>(`/api/receipts/${id}`, { method: 'DELETE', token }),
};

/** Query string for the receipt list; empty filters are left out. */
export function receiptListQuery(page: number, size: number, filters: ReceiptFilters): string {
  const params = [`page=${page}`, `size=${size}`];
  for (const key of ['store', 'month', 'q'] as const) {
    const value = filters[key]?.trim();
    if (value) {
      params.push(`${key}=${encodeURIComponent(value)}`);
    }
  }
  return params.join('&');
}

export type BudgetUpdate = { overall: number | null; categories: { category: string; limit: number }[] };

export const budgetApi = {
  settings: (token: string) => apiRequest<BudgetSettings>('/api/budget', { token }),
  categories: (token: string) => apiRequest<BudgetCategoryOption[]>('/api/budget/categories', { token }),
  save: (token: string, update: BudgetUpdate) =>
    apiRequest<BudgetSettings>('/api/budget', { method: 'PUT', body: update, token }),
  status: (token: string) => apiRequest<BudgetStatus>('/api/insights/budget', { token }),
};

export const insightsApi = {
  priceHistory: (token: string, productId: number) =>
    apiRequest<PriceHistory>(`/api/insights/products/${productId}/prices`, { token }),
  spending: (token: string, months = 6) =>
    apiRequest<SpendingInsight>(`/api/insights/spending?months=${months}`, { token }),
  /** months = 0 means the whole history */
  savings: (token: string, months = 3) =>
    apiRequest<SavingsInsight>(`/api/insights/savings?months=${months}`, { token }),
  bestDay: (token: string, days = 365) => apiRequest<BestDayInsight>(`/api/insights/best-day?days=${days}`, { token }),
  inflation: (token: string, months = 6) =>
    apiRequest<InflationInsight>(`/api/insights/inflation?months=${months}`, { token }),
  summary: (token: string) => apiRequest<InsightSummary>('/api/insights/summary', { token }),
  categoryProducts: (token: string, category: string, months = 6) =>
    apiRequest<CategoryProducts>(
      `/api/insights/categories/${encodeURIComponent(category)}/products?months=${months}`,
      { token },
    ),
};
