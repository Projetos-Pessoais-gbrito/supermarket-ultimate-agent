import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { shoppingApi, type NewShoppingListItem, type ShoppingListItem } from '../api/shopping';
import { useToken } from '../auth/AuthProvider';
import { withChecked } from './text';

// Under ['insights'] so importing or deleting a receipt refreshes suggestions and alerts too
export const shoppingKeys = {
  suggestions: () => ['insights', 'shopping-suggestions'] as const,
  priceAlerts: () => ['insights', 'price-alerts'] as const,
  list: () => ['shopping-list'] as const,
};

export function useShoppingSuggestions() {
  const token = useToken();
  return useQuery({ queryKey: shoppingKeys.suggestions(), queryFn: () => shoppingApi.suggestions(token) });
}

export function usePriceAlerts() {
  const token = useToken();
  return useQuery({ queryKey: shoppingKeys.priceAlerts(), queryFn: () => shoppingApi.priceAlerts(token) });
}

export function useShoppingList() {
  const token = useToken();
  return useQuery({ queryKey: shoppingKeys.list(), queryFn: () => shoppingApi.list(token) });
}

/** The list changed: refresh it and the "already in the list" flags of the suggestions. */
function useRefreshList() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: shoppingKeys.list() }),
      queryClient.invalidateQueries({ queryKey: shoppingKeys.suggestions() }),
    ]);
}

export function useAddToList() {
  const token = useToken();
  const refresh = useRefreshList();
  return useMutation({
    mutationFn: (item: NewShoppingListItem) => shoppingApi.add(token, item),
    onSettled: refresh,
  });
}

export function useToggleItem() {
  const token = useToken();
  const queryClient = useQueryClient();
  const refresh = useRefreshList();
  return useMutation({
    mutationFn: ({ id, checked }: { id: number; checked: boolean }) => shoppingApi.setChecked(token, id, checked),
    // Checking items off in the store must feel instant
    onMutate: async ({ id, checked }) => {
      await queryClient.cancelQueries({ queryKey: shoppingKeys.list() });
      const previous = queryClient.getQueryData<ShoppingListItem[]>(shoppingKeys.list());
      if (previous) {
        queryClient.setQueryData(shoppingKeys.list(), withChecked(previous, id, checked));
      }
      return { previous };
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(shoppingKeys.list(), context.previous);
      }
    },
    onSettled: refresh,
  });
}

export function useRemoveItem() {
  const token = useToken();
  const refresh = useRefreshList();
  return useMutation({ mutationFn: (id: number) => shoppingApi.remove(token, id), onSettled: refresh });
}

export function useClearChecked() {
  const token = useToken();
  const refresh = useRefreshList();
  return useMutation({ mutationFn: () => shoppingApi.removeChecked(token), onSettled: refresh });
}
