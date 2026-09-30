import { apiRequest } from './client';
import type {
  BestDayInsight,
  CategoryProducts,
  InflationInsight,
  InsightSummary,
  Me,
  Page,
  ReceiptDetails,
  ReceiptSummary,
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

export const receiptsApi = {
  import: (token: string, qrCodeUrl: string) =>
    apiRequest<ReceiptDetails>('/api/receipts', { method: 'POST', body: { qrCodeUrl }, token }),
  list: (token: string, page = 0, size = 20) =>
    apiRequest<Page<ReceiptSummary>>(`/api/receipts?page=${page}&size=${size}`, { token }),
  details: (token: string, id: number) => apiRequest<ReceiptDetails>(`/api/receipts/${id}`, { token }),
};

export const insightsApi = {
  spending: (token: string, months = 6) =>
    apiRequest<SpendingInsight>(`/api/insights/spending?months=${months}`, { token }),
  savings: (token: string, days = 90) => apiRequest<SavingsInsight>(`/api/insights/savings?days=${days}`, { token }),
  bestDay: (token: string) => apiRequest<BestDayInsight>('/api/insights/best-day', { token }),
  inflation: (token: string, months = 6) =>
    apiRequest<InflationInsight>(`/api/insights/inflation?months=${months}`, { token }),
  summary: (token: string) => apiRequest<InsightSummary>('/api/insights/summary', { token }),
  categoryProducts: (token: string, category: string) =>
    apiRequest<CategoryProducts>(`/api/insights/categories/${encodeURIComponent(category)}/products`, { token }),
};
