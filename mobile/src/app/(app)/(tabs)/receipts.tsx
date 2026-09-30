import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { errorMessage } from '../../../api/messages';
import type { ReceiptSummary } from '../../../api/types';
import { formatCurrency, formatDateTime } from '../../../format';
import { groupByMonth, monthChipLabel, monthTitle, type ReceiptListRow } from '../../../receipts/monthGroups';
import { useReceiptList } from '../../../receipts/queries';
import { Button, ErrorBanner } from '../../../ui/components';
import { colors, spacing } from '../../../ui/theme';

const SEARCH_DELAY_MS = 350;

export default function ReceiptListScreen() {
  const router = useRouter();
  const [text, setText] = useState('');
  const [q, setQ] = useState('');
  const [store, setStore] = useState<string | undefined>();
  const [month, setMonth] = useState<string | undefined>();
  useEffect(() => {
    // Search once the user pauses typing, not on every key
    const timer = setTimeout(() => setQ(text.trim()), SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [text]);

  const { data, error, isPending, isRefetching, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useReceiptList({ q, store, month });
  const receipts = data?.pages.flatMap(page => page.content) ?? [];
  const first = data?.pages[0];
  const rows = groupByMonth(receipts, first?.monthlyTotals ?? []);
  const filtering = q !== '' || store !== undefined || month !== undefined;
  const hasReceipts = (first?.months.length ?? 0) > 0;

  const clearFilters = () => {
    setText('');
    setQ('');
    setStore(undefined);
    setMonth(undefined);
  };

  return (
    <View style={styles.container}>
      {(hasReceipts || filtering) && (
        <View style={styles.filters}>
          <TextInput
            value={text}
            onChangeText={setText}
            placeholder="Buscar produto nas notas"
            placeholderTextColor={colors.textMuted}
            accessibilityLabel="Buscar produto nas notas"
            returnKeyType="search"
            onSubmitEditing={() => setQ(text.trim())}
            clearButtonMode="while-editing"
            maxLength={100}
            style={styles.search}
          />
          <ChipRow
            label="Mercado"
            options={first?.stores ?? []}
            selected={store}
            onSelect={setStore}
            optionLabel={option => option}
          />
          <ChipRow
            label="Mês"
            options={first?.months ?? []}
            selected={month}
            onSelect={setMonth}
            optionLabel={monthChipLabel}
          />
        </View>
      )}
      {isPending ? (
        <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
      ) : (
        <FlatList
          data={rows}
          keyExtractor={row => row.key}
          renderItem={({ item }) => <Row row={item} />}
          contentContainerStyle={styles.list}
          keyboardShouldPersistTaps="handled"
          refreshControl={<RefreshControl refreshing={isRefetching && !isFetchingNextPage} onRefresh={refetch} />}
          onEndReached={() => hasNextPage && !isFetchingNextPage && fetchNextPage()}
          onEndReachedThreshold={0.5}
          ListHeaderComponent={error ? <ErrorBanner message={errorMessage(error)} /> : null}
          ListEmptyComponent={error ? null : filtering ? <NoMatches onClear={clearFilters} /> : <EmptyState />}
          ListFooterComponent={isFetchingNextPage ? <ActivityIndicator color={colors.primary} /> : null}
        />
      )}
      <View style={styles.footer}>
        <Button title="Escanear nota" onPress={() => router.push('/scan')} />
      </View>
    </View>
  );
}

function ChipRow({
  label,
  options,
  selected,
  onSelect,
  optionLabel,
}: {
  label: string;
  options: string[];
  selected: string | undefined;
  onSelect: (value: string | undefined) => void;
  optionLabel: (option: string) => string;
}) {
  if (options.length < 2 && selected === undefined) {
    return null;
  }
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.chips}
      keyboardShouldPersistTaps="handled">
      <Text style={styles.chipGroup}>{label}:</Text>
      {options.map(option => {
        const active = option === selected;
        return (
          <Pressable
            key={option}
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            accessibilityLabel={`${label} ${optionLabel(option)}${active ? ', toque para remover o filtro' : ''}`}
            onPress={() => onSelect(active ? undefined : option)}
            style={[styles.chip, active && styles.chipActive]}>
            <Text style={[styles.chipText, active && styles.chipTextActive]}>
              {active ? '✓ ' : ''}
              {optionLabel(option)}
            </Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

function Row({ row }: { row: ReceiptListRow }) {
  if (row.type === 'receipt') {
    return <ReceiptRow receipt={row.receipt} />;
  }
  return (
    <View style={styles.monthHeader} accessibilityRole="header">
      <Text style={styles.monthTitle}>{monthTitle(row.month)}</Text>
      {row.total !== null && (
        <Text style={styles.monthTotal}>
          {formatCurrency(row.total)} · {row.receiptCount} {row.receiptCount === 1 ? 'nota' : 'notas'}
        </Text>
      )}
    </View>
  );
}

function ReceiptRow({ receipt }: { receipt: ReceiptSummary }) {
  const router = useRouter();
  // A plain Pressable: wrapping it in <Link asChild> dropped the style function (card layout lost)
  return (
    <Pressable
      accessibilityRole="button"
      onPress={() => router.push({ pathname: '/receipts/[id]', params: { id: String(receipt.id) } })}
      style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}>
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

function NoMatches({ onClear }: { onClear: () => void }) {
  return (
    <View style={styles.empty}>
      <Text style={styles.emptyTitle}>Nenhuma nota encontrada</Text>
      <Text style={styles.emptyText}>Nenhuma nota combina com a busca e os filtros escolhidos.</Text>
      <View style={styles.clear}>
        <Button title="Limpar filtros" variant="secondary" onPress={onClear} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.surface },
  loading: { marginTop: spacing.xl },
  filters: {
    paddingTop: spacing.md,
    paddingBottom: spacing.xs,
    gap: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  search: {
    marginHorizontal: spacing.md,
    minHeight: 44,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: spacing.md,
    fontSize: 16,
    color: colors.text,
    backgroundColor: colors.background,
  },
  chips: { paddingHorizontal: spacing.md, gap: spacing.sm, alignItems: 'center' },
  chipGroup: { fontSize: 13, color: colors.textMuted },
  chip: {
    minHeight: 36,
    justifyContent: 'center',
    paddingHorizontal: spacing.md,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.background,
  },
  chipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { fontSize: 14, color: colors.text },
  chipTextActive: { color: colors.background, fontWeight: '600' },
  list: { padding: spacing.md, gap: spacing.sm, flexGrow: 1 },
  monthHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
    flexWrap: 'wrap',
    marginTop: spacing.sm,
  },
  monthTitle: { fontSize: 15, fontWeight: '700', color: colors.text },
  monthTotal: { fontSize: 14, color: colors.textMuted },
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
  clear: { marginTop: spacing.md, alignSelf: 'stretch' },
  footer: {
    padding: spacing.md,
    paddingBottom: spacing.lg,
    backgroundColor: colors.background,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
  },
});
