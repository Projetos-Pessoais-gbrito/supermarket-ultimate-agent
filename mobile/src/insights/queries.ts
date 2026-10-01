import { useQuery } from '@tanstack/react-query';

import { insightsApi } from '../api/endpoints';
import { useToken } from '../auth/AuthProvider';
import { bestDayWindowDays, type Period } from './period';

export const insightKeys = {
  all: ['insights'] as const,
  spending: (period: Period) => [...insightKeys.all, 'spending', period] as const,
  savings: (period: Period) => [...insightKeys.all, 'savings', period] as const,
  bestDay: (period: Period) => [...insightKeys.all, 'best-day', period] as const,
  inflation: () => [...insightKeys.all, 'inflation'] as const,
  summary: () => [...insightKeys.all, 'summary'] as const,
  category: (category: string, period: Period) => [...insightKeys.all, 'category', category, period] as const,
  priceHistory: (productId: number) => [...insightKeys.all, 'price-history', productId] as const,
};

export function useSpending(period: Period) {
  const token = useToken();
  return useQuery({ queryKey: insightKeys.spending(period), queryFn: () => insightsApi.spending(token, period) });
}

export function useSavings(period: Period) {
  const token = useToken();
  return useQuery({ queryKey: insightKeys.savings(period), queryFn: () => insightsApi.savings(token, period) });
}

export function useBestDay(period: Period) {
  const token = useToken();
  return useQuery({
    queryKey: insightKeys.bestDay(period),
    queryFn: () => insightsApi.bestDay(token, bestDayWindowDays(period)),
  });
}

export function useInflation() {
  const token = useToken();
  return useQuery({ queryKey: insightKeys.inflation(), queryFn: () => insightsApi.inflation(token) });
}

/** AI tips are optional: failures just hide the card, so they never retry or show errors. */
export function useSummary() {
  const token = useToken();
  return useQuery({
    queryKey: insightKeys.summary(),
    queryFn: () => insightsApi.summary(token),
    retry: false,
    staleTime: 5 * 60_000,
  });
}

export function useCategoryProducts(category: string, period: Period) {
  const token = useToken();
  return useQuery({
    queryKey: insightKeys.category(category, period),
    queryFn: () => insightsApi.categoryProducts(token, category, period),
  });
}

export function usePriceHistory(productId: number) {
  const token = useToken();
  return useQuery({
    queryKey: insightKeys.priceHistory(productId),
    queryFn: () => insightsApi.priceHistory(token, productId),
    enabled: Number.isFinite(productId),
  });
}
