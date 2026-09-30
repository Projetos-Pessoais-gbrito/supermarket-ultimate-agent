import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '../../../api/messages';
import type { ReceiptDetails } from '../../../api/types';
import { formatCurrency, formatDateTime, formatQuantity } from '../../../format';
import { groupReceiptItems, type GroupedItem } from '../../../receipts/groupItems';
import { useDeleteReceipt, useReceipt } from '../../../receipts/queries';
import { Button, ErrorBanner } from '../../../ui/components';
import { colors, spacing } from '../../../ui/theme';

export default function ReceiptDetailsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { data: receipt, error, isPending } = useReceipt(Number(id));

  return (
    <View style={styles.container}>
      <Stack.Screen options={{ title: 'Nota fiscal' }} />
      {isPending ? (
        <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
      ) : receipt ? (
        <ReceiptContent receipt={receipt} />
      ) : (
        <View style={styles.content}>
          <ErrorBanner message={errorMessage(error)} />
        </View>
      )}
    </View>
  );
}

function ReceiptContent({ receipt }: { receipt: ReceiptDetails }) {
  const router = useRouter();
  const groups = groupReceiptItems(receipt.items);
  return (
    <ScrollView contentContainerStyle={styles.content}>
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
        {groups.map(group => (
          <ItemRow
            key={group.key}
            group={group}
            onPress={
              group.item.productId === null
                ? undefined
                : () =>
                    router.push({ pathname: '/products/[id]', params: { id: String(group.item.productId) } })
            }
          />
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

      <DeleteReceipt receiptId={receipt.id} />
    </ScrollView>
  );
}

/** Two-step delete (explain, then confirm) for receipts imported by mistake. */
function DeleteReceipt({ receiptId }: { receiptId: number }) {
  const router = useRouter();
  const deleteReceipt = useDeleteReceipt();
  const [confirming, setConfirming] = useState(false);

  if (!confirming) {
    return <Button title="Excluir nota" variant="secondary" onPress={() => setConfirming(true)} />;
  }
  return (
    <View style={styles.card}>
      <Text style={styles.sectionTitle}>Excluir esta nota?</Text>
      <Text style={styles.muted}>Ela sai da sua lista e dos seus gastos. Você pode importá-la de novo depois.</Text>
      {deleteReceipt.error && <ErrorBanner message={errorMessage(deleteReceipt.error)} />}
      <View style={styles.actions}>
        <Button
          title="Excluir nota"
          variant="danger"
          loading={deleteReceipt.isPending}
          onPress={() => deleteReceipt.mutate(receiptId, { onSuccess: () => router.back() })}
        />
        <Button title="Cancelar" variant="secondary" onPress={() => setConfirming(false)} />
      </View>
    </View>
  );
}

function ItemRow({ group, onPress }: { group: GroupedItem; onPress?: () => void }) {
  const { item, count } = group;
  const details = [
    count > 1 ? `${count}× ` : '',
    `${formatQuantity(group.quantity, item.unit)} × ${formatCurrency(item.unitPrice)}`,
    item.categoryLabel ? ` · ${item.categoryLabel}` : '',
  ].join('');
  return (
    <Pressable
      disabled={!onPress}
      onPress={onPress}
      accessibilityRole={onPress ? 'button' : undefined}
      accessibilityHint={onPress ? 'Mostra o histórico de preços' : undefined}
      style={({ pressed }) => [styles.item, pressed && styles.itemPressed]}>
      <View style={styles.itemMain}>
        <Text style={styles.itemName}>{item.productName ?? item.description}</Text>
        {item.productName && <Text style={styles.muted}>{item.description}</Text>}
        <Text style={styles.muted}>{details}</Text>
      </View>
      <Text style={styles.itemTotal}>
        {formatCurrency(group.totalPrice)}
        {onPress ? '  ›' : ''}
      </Text>
    </Pressable>
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
  itemPressed: { opacity: 0.6 },
  actions: { gap: spacing.sm, marginTop: spacing.sm },
  itemMain: { flex: 1, marginRight: spacing.md },
  itemName: { fontSize: 15, color: colors.text },
  itemTotal: { fontSize: 15, fontWeight: '600', color: colors.text },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: spacing.xs },
  summaryLabel: { fontSize: 15, color: colors.textMuted },
  summaryValue: { fontSize: 15, color: colors.text },
  strong: { fontWeight: '700', color: colors.text, fontSize: 17 },
});
