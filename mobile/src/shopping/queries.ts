import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { shoppingApi, type NewShoppingListItem, type ShoppingListItem } from '../api/shopping';
import { useToken } from '../auth/AuthProvider';
import { withChecked } from './text';

// Under ['insights'] so importing or deleting a receipt refreshes suggestions and alerts too
export const shoppingKeys = {
  suggestions: () => ['insights', 'shopping-suggestions'] as const,
  priceAlerts: () => ['insights', 'price-alerts'] as const,
  list: () => ['shopping-list'] as const,
  products: (text: string) => ['insights', 'list-products', text] as const,
  markets: () => ['insights', 'list-markets'] as const,
  allPrices: () => ['insights', 'list-prices'] as const,
  prices: (market: string) => [...shoppingKeys.allPrices(), market] as const,
  saved: () => ['saved-shopping-lists'] as const,
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
      queryClient.invalidateQueries({ queryKey: shoppingKeys.allPrices() }),
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

/** Products the user bought matching what they type; nothing until at least 2 letters. */
export function useProductSearch(text: string) {
  const token = useToken();
  const query = text.trim();
  return useQuery({
    queryKey: shoppingKeys.products(query.toLowerCase()),
    queryFn: () => shoppingApi.searchProducts(token, query),
    enabled: query.length >= 2,
    // Keeps the previous options on screen while the next letters load
    placeholderData: keepPreviousData,
  });
}

export function useMarkets() {
  const token = useToken();
  return useQuery({ queryKey: shoppingKeys.markets(), queryFn: () => shoppingApi.markets(token) });
}

/** Prices of the listed products at the market; idle while no market is chosen. */
export function useListPrices(market: string | null) {
  const token = useToken();
  return useQuery({
    queryKey: shoppingKeys.prices(market ?? ''),
    queryFn: () => shoppingApi.prices(token, market ?? ''),
    enabled: market !== null,
    placeholderData: keepPreviousData,
  });
}

export function useAddFromReceipt() {
  const token = useToken();
  const refresh = useRefreshList();
  return useMutation({
    mutationFn: ({ receiptId, lineNumbers }: { receiptId: number; lineNumbers: number[] }) =>
      shoppingApi.addFromReceipt(token, receiptId, lineNumbers),
    onSettled: refresh,
  });
}

export function useSavedLists() {
  const token = useToken();
  return useQuery({ queryKey: shoppingKeys.saved(), queryFn: () => shoppingApi.savedLists(token) });
}

export function useSaveList() {
  const token = useToken();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => shoppingApi.saveList(token, name),
    onSettled: () => queryClient.invalidateQueries({ queryKey: shoppingKeys.saved() }),
  });
}

export function useApplySavedList() {
  const token = useToken();
  const refresh = useRefreshList();
  return useMutation({ mutationFn: (id: number) => shoppingApi.applySavedList(token, id), onSettled: refresh });
}

export function useRemoveSavedList() {
  const token = useToken();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => shoppingApi.removeSavedList(token, id),
    onSettled: () => queryClient.invalidateQueries({ queryKey: shoppingKeys.saved() }),
  });
}
