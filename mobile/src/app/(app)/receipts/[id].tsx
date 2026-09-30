import { Stack, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '../../../api/messages';
import type { ReceiptDetails, ReceiptItem } from '../../../api/types';
import { formatCurrency, formatDateTime, formatQuantity } from '../../../format';
import { useReceipt } from '../../../receipts/queries';
import { ErrorBanner } from '../../../ui/components';
import { colors, spacing } from '../../../ui/theme';

export default function ReceiptDetailsScreen() {
  const { id, alreadyImported } = useLocalSearchParams<{ id: string; alreadyImported?: string }>();
  const { data: receipt, error, isPending } = useReceipt(Number(id));

  return (
    <View style={styles.container}>
      <Stack.Screen options={{ title: 'Nota fiscal' }} />
      {isPending ? (
        <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
      ) : receipt ? (
        <ReceiptContent receipt={receipt} alreadyImported={alreadyImported === '1'} />
      ) : (
        <View style={styles.content}>
          <ErrorBanner message={errorMessage(error)} />
        </View>
      )}
    </View>
  );
}

function ReceiptContent({ receipt, alreadyImported }: { receipt: ReceiptDetails; alreadyImported: boolean }) {
  return (
    <ScrollView contentContainerStyle={styles.content}>
      {alreadyImported && (
        <View accessibilityRole="alert" style={styles.notice}>
          <Text style={styles.noticeText}>Essa nota já estava na sua lista. Nada foi importado de novo.</Text>
        </View>
      )}
      <View style={styles.card}>
        <Text style={styles.store}>{receipt.store.name}</Text>
        {receipt.store.legalName !== receipt.store.name && (
          <Text style={styles.muted}>{receipt.store.legalName}</Text>
        )}
        <Text style={styles.muted}>{receipt.store.address}</Text>
        <Text style={styles.muted}>{formatDateTime(receipt.issuedAt)}</Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionTitle}>
          {receipt.items.length} {receipt.items.length === 1 ? 'item' : 'itens'}
        </Text>
        {receipt.items.map(item => (
          <ItemRow key={item.lineNumber} item={item} />
        ))}
      </View>

      <View style={styles.card}>
        {receipt.discountAmount > 0 && (
          <SummaryRow label="Descontos" value={`- ${formatCurrency(receipt.discountAmount)}`} />
        )}
        <SummaryRow label="Total pago" value={formatCurrency(receipt.totalAmount)} strong />
        {receipt.payments.map(payment => (
          <SummaryRow key={payment.method} label={payment.method} value={formatCurrency(payment.amount)} />
        ))}
        <SummaryRow label="Tributos aproximados" value={formatCurrency(receipt.approximateTaxes)} />
      </View>
    </ScrollView>
  );
}

function ItemRow({ item }: { item: ReceiptItem }) {
  return (
    <View style={styles.item}>
      <View style={styles.itemMain}>
        <Text style={styles.itemName}>{item.description}</Text>
        <Text style={styles.muted}>
          {formatQuantity(item.quantity, item.unit)} × {formatCurrency(item.unitPrice)}
        </Text>
      </View>
      <Text style={styles.itemTotal}>{formatCurrency(item.totalPrice)}</Text>
    </View>
  );
}

function SummaryRow({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <View style={styles.summaryRow}>
      <Text style={[styles.summaryLabel, strong && styles.strong]}>{label}</Text>
      <Text style={[styles.summaryValue, strong && styles.strong]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.surface },
  loading: { marginTop: spacing.xl },
  content: { padding: spacing.md, gap: spacing.md },
  notice: { backgroundColor: colors.chartTrack, borderRadius: 12, padding: spacing.md },
  noticeText: { fontSize: 14, color: colors.text },
  card: { backgroundColor: colors.background, borderRadius: 12, padding: spacing.md },
  store: { fontSize: 18, fontWeight: '700', color: colors.text, marginBottom: spacing.xs },
  muted: { fontSize: 13, color: colors.textMuted },
  sectionTitle: { fontSize: 15, fontWeight: '600', color: colors.text, marginBottom: spacing.sm },
  item: {
    flexDirection: 'row',
    paddingVertical: spacing.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
  },
  itemMain: { flex: 1, marginRight: spacing.md },
  itemName: { fontSize: 15, color: colors.text },
  itemTotal: { fontSize: 15, fontWeight: '600', color: colors.text },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: spacing.xs },
  summaryLabel: { fontSize: 15, color: colors.textMuted },
  summaryValue: { fontSize: 15, color: colors.text },
  strong: { fontWeight: '700', color: colors.text, fontSize: 17 },
});
