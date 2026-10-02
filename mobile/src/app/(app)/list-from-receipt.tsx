import Ionicons from '@expo/vector-icons/Ionicons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import { ApiError } from '../../api/client';
import { errorMessage } from '../../api/messages';
import type { ReceiptDetails, ReceiptSummary } from '../../api/types';
import { formatCurrency, formatDateTime, formatQuantity } from '../../format';
import { groupReceiptItems, type GroupedItem } from '../../receipts/groupItems';
import { useReceipt, useReceiptList } from '../../receipts/queries';
import { saveMarket } from '../../shopping/market';
import { useAddFromReceipt } from '../../shopping/queries';
import { Button, ErrorBanner } from '../../ui/components';
import { makeStyles, spacing, useColors } from '../../ui/theme';

/**
 * Builds the shopping list from one of the user's receipts: pick the receipt, keep the items to buy
 * again, and the list is priced at that receipt's market.
 */
export default function ListFromReceiptScreen() {
  const params = useLocalSearchParams<{ receiptId?: string }>();
  const [receiptId, setReceiptId] = useState<number | null>(params.receiptId ? Number(params.receiptId) : null);
  return (
    <>
      <Stack.Screen options={{ title: 'Montar com uma nota' }} />
      {receiptId === null ? (
        <ReceiptPicker onPick={setReceiptId} />
      ) : (
        <ItemPicker receiptId={receiptId} onChangeReceipt={params.receiptId ? undefined : () => setReceiptId(null)} />
      )}
    </>
  );
}

function ReceiptPicker({ onPick }: { onPick: (id: number) => void }) {
  const styles = useStyles();
  const colors = useColors();
  const { data, error, isPending, fetchNextPage, hasNextPage, isFetchingNextPage } = useReceiptList();
  const receipts = data?.pages.flatMap(page => page.content) ?? [];
  if (isPending) {
    return <ActivityIndicator style={styles.loading} color={colors.primary} />;
  }
  return (
    <FlatList
      style={styles.container}
      contentContainerStyle={styles.content}
      data={receipts}
      keyExtractor={receipt => String(receipt.id)}
      ListHeaderComponent={
        <View style={styles.header}>
          <ErrorBanner message={error ? errorMessage(error) : null} />
          <Text style={styles.muted}>Escolha a nota com as compras que você quer repetir.</Text>
        </View>
      }
      ListEmptyComponent={
        error ? null : <Text style={styles.muted}>Nenhuma nota ainda. Escaneie uma nota para montar a lista.</Text>
      }
      renderItem={({ item }) => <ReceiptRow receipt={item} onPress={() => onPick(item.id)} />}
      onEndReached={() => hasNextPage && !isFetchingNextPage && fetchNextPage()}
      ListFooterComponent={isFetchingNextPage ? <ActivityIndicator color={colors.primary} /> : null}
    />
  );
}

function ReceiptRow({ receipt, onPress }: { receipt: ReceiptSummary; onPress: () => void }) {
  const styles = useStyles();
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.card, styles.row, pressed && styles.pressed]}>
      <View style={styles.rowMain}>
        <Text style={styles.name} numberOfLines={1}>
          {receipt.storeName}
        </Text>
        <Text style={styles.muted}>
          {formatDateTime(receipt.issuedAt)} · {receipt.itemCount} {receipt.itemCount === 1 ? 'item' : 'itens'}
        </Text>
      </View>
      <Text style={styles.total}>{formatCurrency(receipt.totalAmount)}</Text>
    </Pressable>
  );
}

function ItemPicker({ receiptId, onChangeReceipt }: { receiptId: number; onChangeReceipt?: () => void }) {
  const styles = useStyles();
  const colors = useColors();
  const receipt = useReceipt(receiptId);
  if (receipt.isPending) {
    return <ActivityIndicator style={styles.loading} color={colors.primary} />;
  }
  if (!receipt.data) {
    return (
      <View style={styles.content}>
        <ErrorBanner message={errorMessage(receipt.error)} />
        {onChangeReceipt && <Button title="Escolher outra nota" variant="secondary" onPress={onChangeReceipt} />}
      </View>
    );
  }
  return <ItemChecklist receipt={receipt.data} onChangeReceipt={onChangeReceipt} />;
}

function ItemChecklist({ receipt, onChangeReceipt }: { receipt: ReceiptDetails; onChangeReceipt?: () => void }) {
  const styles = useStyles();
  const router = useRouter();
  const groups = useMemo(() => groupReceiptItems(receipt.items), [receipt.items]);
  // Everything starts selected: usually the user removes a few items rather than picking many
  const [selected, setSelected] = useState<Set<string>>(() => new Set(groups.map(group => group.key)));
  const add = useAddFromReceipt();
  const allSelected = selected.size === groups.length;

  const toggle = (key: string) =>
    setSelected(current => {
      const next = new Set(current);
      if (!next.delete(key)) {
        next.add(key);
      }
      return next;
    });

  const submit = () => {
    const lineNumbers = groups.filter(group => selected.has(group.key)).flatMap(group => group.lineNumbers);
    add.mutate(
      { receiptId: receipt.id, lineNumbers },
      {
        onSuccess: async () => {
          // Price the list at the market the receipt came from
          await saveMarket(receipt.store.name);
          router.dismissTo('/list');
        },
      },
    );
  };

  return (
    <View style={styles.container}>
      <FlatList
        contentContainerStyle={styles.content}
        data={groups}
        keyExtractor={group => group.key}
        ListHeaderComponent={
          <View style={styles.header}>
            <ErrorBanner message={add.error ? addErrorMessage(add.error) : null} />
            <View style={styles.row}>
              <View style={styles.rowMain}>
                <Text style={styles.name}>{receipt.store.name}</Text>
                <Text style={styles.muted}>{formatDateTime(receipt.issuedAt)}</Text>
              </View>
              {onChangeReceipt && (
                <Pressable accessibilityRole="button" hitSlop={8} onPress={onChangeReceipt}>
                  <Text style={styles.action}>Trocar nota</Text>
                </Pressable>
              )}
            </View>
            <Pressable
              accessibilityRole="button"
              hitSlop={8}
              onPress={() => setSelected(allSelected ? new Set() : new Set(groups.map(group => group.key)))}>
              <Text style={styles.action}>{allSelected ? 'Desmarcar todos' : 'Marcar todos'}</Text>
            </Pressable>
          </View>
        }
        renderItem={({ item }) => (
          <ItemRow group={item} checked={selected.has(item.key)} onToggle={() => toggle(item.key)} />
        )}
      />
      <View style={styles.footer}>
        <Button
          title={
            selected.size === 0
              ? 'Escolha os itens'
              : `Adicionar ${selected.size} ${selected.size === 1 ? 'item' : 'itens'} à lista`
          }
          onPress={submit}
          loading={add.isPending}
          disabled={selected.size === 0}
        />
      </View>
    </View>
  );
}

function addErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) {
    return 'Essa nota não existe mais. Volte e escolha outra.';
  }
  return errorMessage(error);
}

function ItemRow({ group, checked, onToggle }: { group: GroupedItem; checked: boolean; onToggle: () => void }) {
  const styles = useStyles();
  const colors = useColors();
  const name = group.item.productName ?? group.item.description;
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked }}
      accessibilityLabel={name}
      onPress={onToggle}
      style={({ pressed }) => [styles.itemRow, pressed && styles.pressed]}>
      <Ionicons
        name={checked ? 'checkbox' : 'square-outline'}
        size={24}
        color={checked ? colors.primary : colors.textMuted}
      />
      <View style={styles.rowMain}>
        <Text style={styles.itemName} numberOfLines={2}>
          {name}
        </Text>
        <Text style={styles.muted}>{formatQuantity(group.quantity, group.item.unit)}</Text>
      </View>
      <Text style={styles.itemPrice}>{formatCurrency(group.totalPrice)}</Text>
    </Pressable>
  );
}

const useStyles = makeStyles(colors => ({
  container: { flex: 1, backgroundColor: colors.surface },
  content: { padding: spacing.md, gap: spacing.sm },
  loading: { marginTop: spacing.xl },
  header: { gap: spacing.sm, marginBottom: spacing.sm },
  muted: { fontSize: 13, color: colors.textMuted },
  card: {
    backgroundColor: colors.background,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    padding: spacing.md,
  },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  rowMain: { flex: 1, gap: 2 },
  pressed: { opacity: 0.7 },
  name: { fontSize: 16, fontWeight: '600', color: colors.text },
  total: { fontSize: 16, fontWeight: '700', color: colors.text },
  action: { fontSize: 15, fontWeight: '600', color: colors.primary },
  itemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    backgroundColor: colors.background,
    borderRadius: 10,
  },
  itemName: { fontSize: 15, color: colors.text },
  itemPrice: { fontSize: 14, color: colors.textMuted },
  footer: {
    padding: spacing.md,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
    backgroundColor: colors.background,
  },
}));
