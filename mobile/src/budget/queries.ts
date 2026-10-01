import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { budgetApi, type BudgetUpdate } from '../api/endpoints';
import { useToken } from '../auth/AuthProvider';
import { insightKeys } from '../insights/queries';

export const budgetKeys = {
  settings: ['budget', 'settings'] as const,
  categories: ['budget', 'categories'] as const,
  // Under insights so importing or deleting a receipt refreshes it with the other insights
  status: [...insightKeys.all, 'budget'] as const,
};

export function useBudgetStatus() {
  const token = useToken();
  return useQuery({ queryKey: budgetKeys.status, queryFn: () => budgetApi.status(token) });
}

export function useBudgetSettings() {
  const token = useToken();
  return useQuery({ queryKey: budgetKeys.settings, queryFn: () => budgetApi.settings(token) });
}

export function useBudgetCategories() {
  const token = useToken();
  return useQuery({
    queryKey: budgetKeys.categories,
    queryFn: () => budgetApi.categories(token),
    staleTime: Infinity,
  });
}

export function useSaveBudget() {
  const token = useToken();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (update: BudgetUpdate) => budgetApi.save(token, update),
    onSuccess: settings => {
      queryClient.setQueryData(budgetKeys.settings, settings);
      return queryClient.invalidateQueries({ queryKey: budgetKeys.status });
    },
  });
}
