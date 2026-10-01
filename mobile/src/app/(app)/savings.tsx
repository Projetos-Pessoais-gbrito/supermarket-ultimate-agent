import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { ActivityIndicator, FlatList, Pressable, Text, View } from 'react-native';

import { errorMessage } from '../../api/messages';
import type { SavingsDetails, SavingsPurchase } from '../../api/types';
import { formatCurrency, formatDate } from '../../format';
import { parsePeriod, periodDescription } from '../../insights/period';
import { useSavingsDetails } from '../../insights/queries';
import { formatWindow, purchasePaid, purchaseReference } from '../../insights/savingsText';
import { ErrorBanner } from '../../ui/components';
import { makeStyles, spacing, useColors } from '../../ui/theme';

type ProductDetails = SavingsDetails['products'][number];

/** How "Economia possível" is calculated, with every purchase behind the total. */
export default function SavingsScreen() {
  const colors = useColors();
  const styles = useStyles();
  const { months } = useLocalSearchParams<{ months?: string }>();
  const period = parsePeriod(months);
  const { data, error, isPending } = useSavingsDetails(period);

  return (
    <View style={styles.container}>
      <Stack.Screen options={{ title: 'Economia possível' }} />
      {isPending ? (
        <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
      ) : !data ? (
        <View style={styles.list}>
          <ErrorBanner message={errorMessage(error)} />
        </View>
      ) : (
        <FlatList
          data={data.products}
          keyExtractor={product => String(product.productId)}
          contentContainerStyle={styles.list}
          ListHeaderComponent={<Explanation details={data} periodText={periodDescription(period)} />}
          ListEmptyComponent={
            <Text style={styles.muted}>
              Ainda não há o que comparar. Quando você comprar o mesmo produto mais de uma vez em até{' '}
              {formatWindow(data.comparisonWindowDays)}, as diferenças de preço aparecem aqui.
            </Text>
          }
          renderItem={({ item }) => <ProductCard product={item} windowDays={data.comparisonWindowDays} />}
        />
      )}
    </View>
  );
}

function Explanation({ details, periodText }: { details: SavingsDetails; periodText: string }) {
  const styles = useStyles();
  const window = formatWindow(details.comparisonWindowDays);
  return (
    <View style={styles.header}>
      <View style={styles.card}>
        <Text style={styles.muted}>{periodText}</Text>
        <Text style={styles.total}>{formatCurrency(details.potentialSavings)}</Text>
        <Text style={styles.text}>
          É quanto você teria economizado se, em cada compra, tivesse pago o menor preço que você mesmo já pagou pelo
          produto.
        </Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Como calculamos</Text>
        <Step number={1} text="Pegamos cada produto que você comprou mais de uma vez." />
        <Step
          number={2}
          text={`Para cada compra, procuramos o menor preço que você pagou pelo mesmo produto até ${window} antes ou depois, em qualquer mercado.`}
        />
        <Step number={3} text="A diferença entre os dois preços, vezes a quantidade, é o que você poderia ter guardado." />
        <Text style={[styles.muted, styles.note]}>
          Usamos só os preços das suas notas. Compras com mais de {window} de diferença não são comparadas, porque os
          preços mudam com a inflação.
        </Text>
      </View>

      {details.products.length > 0 && <Text style={styles.sectionTitle}>Produto por produto</Text>}
    </View>
  );
}

function Step({ number, text }: { number: number; text: string }) {
  const styles = useStyles();
  return (
    <View style={styles.step}>
      <Text style={styles.stepNumber}>{number}</Text>
      <Text style={[styles.text, styles.stepText]}>{text}</Text>
    </View>
  );
}

function ProductCard({ product, windowDays }: { product: ProductDetails; windowDays: number }) {
  const styles = useStyles();
  const router = useRouter();
  return (
    <View style={styles.card}>
      <Pressable
        onPress={() => router.push({ pathname: '/products/[id]', params: { id: String(product.productId) } })}
        accessibilityRole="button"
        accessibilityHint="Mostra o histórico de preços"
        style={({ pressed }) => [styles.productHeader, pressed && styles.pressed]}>
        <Text style={styles.productName}>{product.name}  ›</Text>
        <Text style={styles.extra}>+{formatCurrency(product.extraPaid)}</Text>
      </Pressable>
      {product.purchases.map((purchase, index) => (
        <PurchaseRow key={`${purchase.receiptId}-${index}`} purchase={purchase} windowDays={windowDays} />
      ))}
    </View>
  );
}

function PurchaseRow({ purchase, windowDays }: { purchase: SavingsPurchase; windowDays: number }) {
  const styles = useStyles();
  return (
    <View style={styles.purchase}>
      <View style={styles.purchaseMain}>
        <Text style={styles.purchaseTitle}>
          {formatDate(purchase.issuedAt)} · {purchase.storeName}
        </Text>
        <Text style={styles.muted}>{purchasePaid(purchase)}</Text>
        <Text style={styles.muted}>{purchaseReference(purchase, windowDays)}</Text>
      </View>
      <Text style={styles.purchaseExtra}>+{formatCurrency(purchase.extraPaid)}</Text>
    </View>
  );
}

const useStyles = makeStyles(colors => ({
  container: { flex: 1, backgroundColor: colors.surface },
  loading: { marginTop: spacing.xl },
  list: { padding: spacing.md, gap: spacing.md },
  header: { gap: spacing.md },
  card: { backgroundColor: colors.background, borderRadius: 12, padding: spacing.md },
  cardTitle: { fontSize: 16, fontWeight: '700', color: colors.text, marginBottom: spacing.sm },
  sectionTitle: { fontSize: 16, fontWeight: '700', color: colors.text, marginTop: spacing.sm },
  total: { fontSize: 32, fontWeight: '700', color: colors.text, marginVertical: spacing.xs },
  text: { fontSize: 15, color: colors.text, lineHeight: 21 },
  muted: { fontSize: 13, color: colors.textMuted, lineHeight: 18 },
  note: { marginTop: spacing.sm },
  step: { flexDirection: 'row', marginBottom: spacing.sm },
  stepNumber: { width: 24, fontSize: 15, fontWeight: '700', color: colors.primary },
  stepText: { flex: 1 },
  productHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: spacing.xs },
  pressed: { opacity: 0.6 },
  productName: { flex: 1, fontSize: 15, fontWeight: '700', color: colors.text, marginRight: spacing.md },
  extra: { fontSize: 15, fontWeight: '700', color: colors.text },
  purchase: {
    flexDirection: 'row',
    paddingVertical: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  purchaseMain: { flex: 1, marginRight: spacing.md, gap: 2 },
  purchaseTitle: { fontSize: 14, fontWeight: '600', color: colors.text },
  purchaseExtra: { fontSize: 14, fontWeight: '600', color: colors.text },
}));
