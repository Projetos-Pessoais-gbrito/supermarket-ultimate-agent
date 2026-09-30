import { useQuery } from '@tanstack/react-query';

import { insightsApi } from '../api/endpoints';
import { useToken } from '../auth/AuthProvider';

export const insightKeys = {
  all: ['insights'] as const,
  spending: () => [...insightKeys.all, 'spending'] as const,
  savings: () => [...insightKeys.all, 'savings'] as const,
  bestDay: () => [...insightKeys.all, 'best-day'] as const,
  inflation: () => [...insightKeys.all, 'inflation'] as const,
  summary: () => [...insightKeys.all, 'summary'] as const,
};

export function useSpending() {
  const token = useToken();
  return useQuery({ queryKey: insightKeys.spending(), queryFn: () => insightsApi.spending(token) });
}

export function useSavings() {
  const token = useToken();
  return useQuery({ queryKey: insightKeys.savings(), queryFn: () => insightsApi.savings(token) });
}

export function useBestDay() {
  const token = useToken();
  return useQuery({ queryKey: insightKeys.bestDay(), queryFn: () => insightsApi.bestDay(token) });
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
