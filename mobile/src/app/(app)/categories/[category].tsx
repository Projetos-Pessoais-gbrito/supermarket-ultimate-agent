import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '../../../api/messages';
import type { CategoryProducts } from '../../../api/types';
import { formatCurrency, formatDate } from '../../../format';
import { parsePeriod, periodDescription } from '../../../insights/period';
import { useCategoryProducts } from '../../../insights/queries';
import { ErrorBanner } from '../../../ui/components';
import { colors, spacing } from '../../../ui/theme';

type CategoryProduct = CategoryProducts['products'][number];

export default function CategoryProductsScreen() {
  const { category, months } = useLocalSearchParams<{ category: string; months?: string }>();
  const period = parsePeriod(months);
  const { data, error, isPending } = useCategoryProducts(category, period);

  return (
    <View style={styles.container}>
      <Stack.Screen options={{ title: data?.label ?? 'Categoria' }} />
      {isPending ? (
        <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
      ) : !data ? (
        <View style={styles.list}>
          <ErrorBanner message={errorMessage(error)} />
        </View>
      ) : (
        <FlatList
          data={data.products}
          keyExtractor={product => `${product.productId ?? 'item'}-${product.name}`}
          contentContainerStyle={styles.list}
          ListHeaderComponent={
            <View style={styles.header}>
              <Text style={styles.muted}>Total · {periodDescription(period)}</Text>
              <Text style={styles.total}>{formatCurrency(data.total)}</Text>
              <Text style={styles.muted}>
                {data.products.length} {data.products.length === 1 ? 'produto' : 'produtos'}
              </Text>
            </View>
          }
          ListEmptyComponent={<Text style={styles.muted}>Nenhum produto nesta categoria no período.</Text>}
          renderItem={({ item }) => <ProductRow product={item} />}
        />
      )}
    </View>
  );
}

function ProductRow({ product }: { product: CategoryProduct }) {
  const router = useRouter();
  const productId = product.productId;
  return (
    <Pressable
      disabled={productId === null}
      onPress={() =>
        productId !== null && router.push({ pathname: '/products/[id]', params: { id: String(productId) } })
      }
      accessibilityRole={productId === null ? undefined : 'button'}
      accessibilityHint={productId === null ? undefined : 'Mostra o histórico de preços'}
      style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}>
      <View style={styles.rowMain}>
        <Text style={styles.name}>{product.name}</Text>
        <Text style={styles.muted}>
          {product.timesBought}× · último: {formatCurrency(product.lastUnitPrice)} no {product.lastStoreName} em{' '}
          {formatDate(product.lastBoughtAt)}
        </Text>
      </View>
      <Text style={styles.value}>
        {formatCurrency(product.totalSpent)}
        {productId === null ? '' : '  ›'}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.surface },
  loading: { marginTop: spacing.xl },
  list: { padding: spacing.md, gap: spacing.sm },
  header: { backgroundColor: colors.background, borderRadius: 12, padding: spacing.md, marginBottom: spacing.sm },
  total: { fontSize: 32, fontWeight: '700', color: colors.text, marginVertical: spacing.xs },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.background,
    borderRadius: 12,
    padding: spacing.md,
  },
  rowPressed: { opacity: 0.6 },
  rowMain: { flex: 1, marginRight: spacing.md },
  name: { fontSize: 15, fontWeight: '600', color: colors.text, marginBottom: 2 },
  muted: { fontSize: 13, color: colors.textMuted },
  value: { fontSize: 15, fontWeight: '700', color: colors.text },
});
