import { Link, Stack } from 'expo-router';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '../../api/messages';
import type { ReceiptSummary } from '../../api/types';
import { formatCurrency, formatDateTime } from '../../format';
import { useReceiptList } from '../../receipts/queries';
import { ErrorBanner } from '../../ui/components';
import { colors, spacing } from '../../ui/theme';

export default function ReceiptListScreen() {
  const { data, error, isPending, isRefetching, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useReceiptList();
  const receipts = data?.pages.flatMap(page => page.content) ?? [];

  return (
    <View style={styles.container}>
      <Stack.Screen options={{ title: 'Minhas notas' }} />
      {isPending ? (
        <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
      ) : (
        <FlatList
          data={receipts}
          keyExtractor={receipt => String(receipt.id)}
          renderItem={({ item }) => <ReceiptRow receipt={item} />}
          contentContainerStyle={styles.list}
          refreshControl={<RefreshControl refreshing={isRefetching} onRefresh={refetch} />}
          onEndReached={() => hasNextPage && !isFetchingNextPage && fetchNextPage()}
          onEndReachedThreshold={0.5}
          ListHeaderComponent={error ? <ErrorBanner message={errorMessage(error)} /> : null}
          ListEmptyComponent={error ? null : <EmptyState />}
          ListFooterComponent={isFetchingNextPage ? <ActivityIndicator color={colors.primary} /> : null}
        />
      )}
    </View>
  );
}

function ReceiptRow({ receipt }: { receipt: ReceiptSummary }) {
  return (
    <Link href={`/receipts/${receipt.id}`} asChild>
      <Pressable accessibilityRole="button" style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}>
        <View style={styles.rowMain}>
          <Text style={styles.store} numberOfLines={1}>
            {receipt.storeName}
          </Text>
          <Text style={styles.meta}>
            {formatDateTime(receipt.issuedAt)} · {receipt.itemCount} {receipt.itemCount === 1 ? 'item' : 'itens'}
          </Text>
        </View>
        <Text style={styles.total}>{formatCurrency(receipt.totalAmount)}</Text>
      </Pressable>
    </Link>
  );
}

function EmptyState() {
  return (
    <View style={styles.empty}>
      <Text style={styles.emptyTitle}>Nenhuma nota ainda</Text>
      <Text style={styles.emptyText}>
        Escaneie o QR code da sua nota fiscal (NFC-e) para acompanhar seus gastos e preços.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.surface },
  loading: { marginTop: spacing.xl },
  list: { padding: spacing.md, gap: spacing.sm, flexGrow: 1 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.background,
    borderRadius: 12,
    padding: spacing.md,
  },
  rowPressed: { opacity: 0.7 },
  rowMain: { flex: 1, marginRight: spacing.md },
  store: { fontSize: 16, fontWeight: '600', color: colors.text },
  meta: { fontSize: 13, color: colors.textMuted, marginTop: 2 },
  total: { fontSize: 16, fontWeight: '700', color: colors.text },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.lg },
  emptyTitle: { fontSize: 20, fontWeight: '700', color: colors.text, marginBottom: spacing.sm },
  emptyText: { fontSize: 15, color: colors.textMuted, textAlign: 'center' },
});
