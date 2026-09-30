import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { receiptsApi } from '../api/endpoints';
import { useToken } from '../auth/AuthProvider';

const PAGE_SIZE = 20;

export const receiptKeys = {
  all: ['receipts'] as const,
  list: () => [...receiptKeys.all, 'list'] as const,
  details: (id: number) => [...receiptKeys.all, 'details', id] as const,
};

export function useReceiptList() {
  const token = useToken();
  return useInfiniteQuery({
    queryKey: receiptKeys.list(),
    queryFn: ({ pageParam }) => receiptsApi.list(token, pageParam, PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: last => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
}

export function useImportReceipt() {
  const token = useToken();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (qrCodeUrl: string) => receiptsApi.import(token, qrCodeUrl.trim()),
    onSuccess: receipt => {
      queryClient.setQueryData(receiptKeys.details(receipt.id), receipt);
      return queryClient.invalidateQueries({ queryKey: receiptKeys.list() });
    },
  });
}

export function useReceipt(id: number) {
  const token = useToken();
  return useQuery({
    queryKey: receiptKeys.details(id),
    queryFn: () => receiptsApi.details(token, id),
    enabled: Number.isFinite(id),
  });
}
