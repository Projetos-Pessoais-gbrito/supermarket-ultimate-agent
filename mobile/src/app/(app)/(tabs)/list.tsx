import Ionicons from '@expo/vector-icons/Ionicons';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { ApiError } from '../../../api/client';
import { errorMessage } from '../../../api/messages';
import type { PriceAlert, ProductOption, ShoppingListItem, ShoppingSuggestion } from '../../../api/shopping';
import { formatCurrency } from '../../../format';
import { loadMarket, saveMarket } from '../../../shopping/market';
import { MarketPicker } from '../../../shopping/MarketPicker';
import {
  useAddToList,
  useClearChecked,
  useListPrices,
  useMarkets,
  usePriceAlerts,
  useProductSearch,
  useRemoveItem,
  useShoppingList,
  useShoppingSuggestions,
  useToggleItem,
} from '../../../shopping/queries';
import { SavedLists } from '../../../shopping/SavedLists';
import {
  alertHeadline,
  formatListQuantity,
  formatUnitPrice,
  formatUsualQuantity,
  itemPrice,
  listEstimate,
  suggestionBestPrice,
  suggestionRhythm,
  unpricedNote,
  type ItemPrice,
} from '../../../shopping/text';
import { Button, ErrorBanner, TextField } from '../../../ui/components';
import { makeStyles, spacing, useColors } from '../../../ui/theme';

/** The user's shopping list, what is probably running out, and notable price changes. */
export default function ShoppingListScreen() {
  const styles = useStyles();
  const list = useShoppingList();
  const suggestions = useShoppingSuggestions();
  const alerts = usePriceAlerts();
  const refreshing = list.isRefetching || suggestions.isRefetching || alerts.isRefetching;

  const refresh = () => {
    list.refetch();
    suggestions.refetch();
    alerts.refetch();
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <MyList items={list.data} error={list.error} loading={list.isPending} />
      <SavedLists canSave={(list.data?.length ?? 0) > 0} />
      <Suggestions items={suggestions.data?.items} error={suggestions.error} loading={suggestions.isPending} />
      <Alerts items={alerts.data?.items} error={alerts.error} loading={alerts.isPending} />
    </ScrollView>
  );
}

function listErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 400) {
    return 'Escreva o nome do item (até 200 letras).';
  }
  if (error instanceof ApiError && error.status === 404) {
    return 'Esse item não está mais na lista. Puxe para atualizar.';
  }
  return errorMessage(error);
}

function MyList({ items, error, loading }: { items?: ShoppingListItem[]; error: Error | null; loading: boolean }) {
  const styles = useStyles();
  const colors = useColors();
  const router = useRouter();
  const [name, setName] = useState('');
  const [market, setMarket] = useState<string | null>(null);
  const searchText = useDebounced(name, 250);
  const options = useProductSearch(searchText);
  const markets = useMarkets();
  const prices = useListPrices(market);
  const add = useAddToList();
  const toggle = useToggleItem();
  const remove = useRemoveItem();
  const clearChecked = useClearChecked();
  const mutationError = add.error ?? toggle.error ?? remove.error ?? clearChecked.error;
  const hasChecked = items?.some(item => item.checked) ?? false;
  const listPrices = market && prices.data?.market === market ? prices.data.items : null;
  const estimate = listPrices && items && items.length > 0 ? listEstimate(items, listPrices) : null;
  const visibleOptions = name.trim().length >= 2 ? (options.data ?? []) : [];

  // The market can also be chosen on the "from a receipt" screen, so read it again on focus
  useFocusEffect(
    useCallback(() => {
      loadMarket().then(setMarket);
    }, []),
  );

  const chooseMarket = (next: string | null) => {
    setMarket(next);
    saveMarket(next);
  };

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) {
      return;
    }
    add.mutate({ name: trimmed }, { onSuccess: () => setName('') });
  };

  const addProduct = (option: ProductOption) => {
    add.mutate({ productId: option.productId }, { onSuccess: () => setName('') });
  };

  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Minha lista</Text>
      <ErrorBanner message={error ? errorMessage(error) : mutationError ? listErrorMessage(mutationError) : null} />
      {markets.data && <MarketPicker markets={markets.data} value={market} onChange={chooseMarket} />}
      <View>
        <TextField
          label="Adicionar item"
          placeholder="Ex.: arroz, detergente"
          value={name}
          onChangeText={setName}
          onSubmitEditing={submit}
          returnKeyType="done"
          maxLength={200}
        />
        {visibleOptions.length > 0 && (
          <View style={[styles.card, styles.options]}>
            {visibleOptions.map(option => (
              <Pressable
                key={option.productId}
                accessibilityRole="button"
                accessibilityLabel={`Adicionar ${option.name} à lista`}
                onPress={() => addProduct(option)}
                style={({ pressed }) => [styles.option, pressed && styles.pressed]}>
                <Text style={styles.optionText} numberOfLines={2}>
                  {option.name}
                </Text>
                <Ionicons name="add-circle-outline" size={22} color={colors.primary} />
              </Pressable>
            ))}
          </View>
        )}
      </View>
      <Button title="Adicionar" onPress={submit} loading={add.isPending} disabled={!name.trim()} />
      <Button title="Montar com uma nota" variant="secondary" onPress={() => router.push('/list-from-receipt')} />
      {loading ? (
        <ActivityIndicator color={colors.primary} />
      ) : items && items.length > 0 ? (
        <View style={styles.card}>
          {items.map(item => (
            <ListRow
              key={item.id}
              item={item}
              price={listPrices ? itemPrice(item, listPrices) : undefined}
              onToggle={() => toggle.mutate({ id: item.id, checked: !item.checked })}
              onRemove={() => remove.mutate(item.id)}
            />
          ))}
          {estimate && market && (
            <View style={styles.totalRow} accessibilityRole="summary">
              <View style={styles.rowMain}>
                <Text style={styles.totalLabel}>Total estimado no {market}</Text>
                <Text style={styles.meta}>Preços da sua última nota desse mercado</Text>
                {estimate.unpriced > 0 && <Text style={styles.meta}>{unpricedNote(estimate.unpriced)}</Text>}
              </View>
              <Text style={styles.totalValue}>{formatCurrency(estimate.total)}</Text>
            </View>
          )}
        </View>
      ) : (
        <Text style={styles.muted}>
          Sua lista está vazia. Adicione itens, monte com uma nota ou use uma lista salva.
        </Text>
      )}
      {hasChecked && (
        <Button
          title="Limpar comprados"
          variant="secondary"
          onPress={() => clearChecked.mutate()}
          loading={clearChecked.isPending}
        />
      )}
    </View>
  );
}

/** The value once it stopped changing for delayMs (avoids a search per keystroke). */
function useDebounced<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}

/** price: undefined when no market is chosen, null when the item has no price there. */
function ListRow({
  item,
  price,
  onToggle,
  onRemove,
}: {
  item: ShoppingListItem;
  price: ItemPrice | null | undefined;
  onToggle: () => void;
  onRemove: () => void;
}) {
  const styles = useStyles();
  const colors = useColors();
  const router = useRouter();
  const productId = item.productId;
  const quantity = formatListQuantity(item.quantity);
  return (
    <View style={styles.listRow}>
      <Pressable
        accessibilityRole="checkbox"
        accessibilityState={{ checked: item.checked }}
        accessibilityLabel={item.name}
        hitSlop={8}
        onPress={onToggle}>
        <Ionicons
          name={item.checked ? 'checkbox' : 'square-outline'}
          size={24}
          color={item.checked ? colors.primary : colors.textMuted}
        />
      </Pressable>
      <Pressable
        style={styles.rowMain}
        disabled={productId === null}
        onPress={() =>
          productId !== null && router.push({ pathname: '/products/[id]', params: { id: String(productId) } })
        }>
        <Text style={[styles.itemName, item.checked && styles.itemChecked]} numberOfLines={2}>
          {quantity ? `${quantity} ` : ''}
          {item.name}
        </Text>
      </Pressable>
      {price !== undefined && (
        <View style={styles.price}>
          {price ? (
            <>
              <Text style={[styles.priceValue, item.checked && styles.itemChecked]}>
                {formatCurrency(price.total)}
              </Text>
              <Text style={styles.meta}>{formatUnitPrice(price.unitPrice, price.unit)}</Text>
            </>
          ) : (
            <Text style={styles.meta}>Sem preço aqui</Text>
          )}
        </View>
      )}
      <Pressable accessibilityRole="button" accessibilityLabel={`Remover ${item.name}`} hitSlop={8} onPress={onRemove}>
        <Ionicons name="close" size={20} color={colors.textMuted} />
      </Pressable>
    </View>
  );
}

function Suggestions({
  items,
  error,
  loading,
}: {
  items?: ShoppingSuggestion[];
  error: Error | null;
  loading: boolean;
}) {
  const styles = useStyles();
  const colors = useColors();
  const router = useRouter();
  const add = useAddToList();
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Está na hora de comprar</Text>
      <Text style={styles.muted}>Produtos que você compra com frequência e que já devem estar acabando.</Text>
      <ErrorBanner message={error ? errorMessage(error) : add.error ? listErrorMessage(add.error) : null} />
      {loading ? (
        <ActivityIndicator color={colors.primary} />
      ) : items && items.length > 0 ? (
        items.map(suggestion => {
          const best = suggestionBestPrice(suggestion);
          const adding = add.isPending && add.variables && 'productId' in add.variables
            && add.variables.productId === suggestion.productId;
          return (
            <Pressable
              key={suggestion.productId}
              accessibilityRole="button"
              onPress={() =>
                router.push({ pathname: '/products/[id]', params: { id: String(suggestion.productId) } })
              }
              style={({ pressed }) => [styles.card, styles.suggestion, pressed && styles.pressed]}>
              <View style={styles.rowMain}>
                <Text style={styles.itemName} numberOfLines={2}>
                  {suggestion.name}
                </Text>
                <Text style={styles.meta}>{suggestionRhythm(suggestion)}</Text>
                <Text style={styles.meta}>
                  Último preço: {formatCurrency(suggestion.lastPrice)} · costuma levar{' '}
                  {formatUsualQuantity(suggestion.usualQuantity)} por vez
                </Text>
                {best && <Text style={styles.highlight}>{best}</Text>}
              </View>
              {suggestion.inList ? (
                <Text style={styles.inList}>Na lista</Text>
              ) : (
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel={`Adicionar ${suggestion.name} à lista`}
                  hitSlop={8}
                  disabled={!!adding}
                  onPress={() =>
                    add.mutate({ productId: suggestion.productId, quantity: suggestion.usualQuantity })
                  }>
                  {adding ? (
                    <ActivityIndicator color={colors.primary} />
                  ) : (
                    <Ionicons name="add-circle-outline" size={28} color={colors.primary} />
                  )}
                </Pressable>
              )}
            </Pressable>
          );
        })
      ) : (
        !error && (
          <Text style={styles.muted}>
            Nada por enquanto. As sugestões aparecem quando você já comprou um produto em pelo menos duas idas ao
            mercado.
          </Text>
        )
      )}
    </View>
  );
}

function Alerts({ items, error, loading }: { items?: PriceAlert[]; error: Error | null; loading: boolean }) {
  const styles = useStyles();
  const colors = useColors();
  const router = useRouter();
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Alertas de preço</Text>
      <Text style={styles.muted}>Sua última compra comparada com o que você costuma pagar.</Text>
      <ErrorBanner message={error ? errorMessage(error) : null} />
      {loading ? (
        <ActivityIndicator color={colors.primary} />
      ) : items && items.length > 0 ? (
        items.map(alert => (
          <Pressable
            key={alert.productId}
            accessibilityRole="button"
            onPress={() => router.push({ pathname: '/products/[id]', params: { id: String(alert.productId) } })}
            style={({ pressed }) => [styles.card, styles.suggestion, pressed && styles.pressed]}>
            <Ionicons
              name={alert.type === 'DEAL' ? 'trending-down' : 'trending-up'}
              size={24}
              color={alert.type === 'DEAL' ? colors.primary : colors.danger}
            />
            <View style={styles.rowMain}>
              <Text style={styles.itemName} numberOfLines={2}>
                {alert.name}
              </Text>
              <Text style={[styles.meta, { color: alert.type === 'DEAL' ? colors.primary : colors.danger }]}>
                {alertHeadline(alert)}
              </Text>
              <Text style={styles.meta}>
                Pagou {formatCurrency(alert.latestPrice)} em {alert.store}
              </Text>
            </View>
          </Pressable>
        ))
      ) : (
        !error && <Text style={styles.muted}>Nenhuma mudança de preço importante nas suas últimas compras.</Text>
      )}
    </View>
  );
}

const useStyles = makeStyles(colors => ({
  container: { flex: 1, backgroundColor: colors.surface },
  content: { padding: spacing.md, gap: spacing.lg },
  section: { gap: spacing.sm },
  sectionTitle: { fontSize: 18, fontWeight: '700', color: colors.text },
  muted: { fontSize: 14, color: colors.textMuted },
  card: {
    backgroundColor: colors.background,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    paddingHorizontal: spacing.md,
  },
  suggestion: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.md },
  pressed: { opacity: 0.7 },
  listRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingVertical: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  rowMain: { flex: 1, gap: 2 },
  itemName: { fontSize: 16, fontWeight: '600', color: colors.text },
  itemChecked: { textDecorationLine: 'line-through', color: colors.textMuted, fontWeight: '400' },
  meta: { fontSize: 13, color: colors.textMuted },
  highlight: { fontSize: 13, fontWeight: '600', color: colors.primary },
  inList: { fontSize: 13, fontWeight: '600', color: colors.textMuted },
  options: { marginTop: -spacing.sm, marginBottom: spacing.md },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    minHeight: 44,
    paddingVertical: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  optionText: { flex: 1, fontSize: 15, color: colors.text },
  price: { alignItems: 'flex-end', maxWidth: '35%' },
  priceValue: { fontSize: 15, fontWeight: '700', color: colors.text },
  totalRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.md },
  totalLabel: { fontSize: 16, fontWeight: '700', color: colors.text },
  totalValue: { fontSize: 18, fontWeight: '700', color: colors.primary },
}));
