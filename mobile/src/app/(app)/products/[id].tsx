import { Stack, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '../../../api/messages';
import type { PricePoint } from '../../../api/types';
import { formatCurrency, formatDate, formatPercent, formatQuantity, formatUnitPrice, isSoldByMeasure } from '../../../format';
import { priceStats } from '../../../insights/priceStats';
import { usePriceHistory } from '../../../insights/queries';
import { ColumnChart } from '../../../ui/charts';
import { ErrorBanner } from '../../../ui/components';
import { makeStyles, spacing, useColors } from '../../../ui/theme';

/** Every price the user paid for a product, with where it was cheapest. */
export default function ProductPriceHistoryScreen() {
  const colors = useColors();
  const styles = useStyles();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { data, error, isPending } = usePriceHistory(Number(id));
  const stats = data ? priceStats(data.prices) : null;
  // Items sold by weight are compared per kilo; what was paid depends on how much was bought
  const unit = stats?.last.unit ?? 'UN';
  const byMeasure = isSoldByMeasure(unit);

  return (
    <View style={styles.container}>
      <Stack.Screen options={{ title: 'Histórico de preços' }} />
      {isPending ? (
        <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
      ) : !data || !stats ? (
        <View style={styles.content}>
          <ErrorBanner message={errorMessage(error)} />
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.content}>
          <View style={styles.card}>
            <Text style={styles.name}>{data.name}</Text>
            <Text style={styles.muted}>
              {data.prices.length} {data.prices.length === 1 ? 'compra' : 'compras'}
            </Text>
          </View>

          <View style={styles.tiles}>
            <Tile
              label="Última compra"
              value={formatCurrency(paid(stats.last))}
              note={byMeasure ? `${amount(stats.last)}
${where(stats.last)}` : where(stats.last)}
            />
            <Tile
              label={byMeasure ? `Melhor preço por ${unit.toLowerCase()}` : 'Melhor preço'}
              value={formatUnitPrice(stats.lowest.unitPrice, unit)}
              note={where(stats.lowest)}
            />
          </View>
          <View style={styles.card}>
            <Text style={styles.muted}>
              Média {formatUnitPrice(stats.average, unit)} · maior {formatUnitPrice(stats.highest.unitPrice, unit)}
            </Text>
            {stats.lastAboveLowestPercent > 0 && (
              <Text style={styles.text}>
                Na última compra você pagou {formatPercent(stats.lastAboveLowestPercent)} acima do seu melhor preço
                {byMeasure ? ` por ${unit.toLowerCase()}` : ''}.
              </Text>
            )}
          </View>

          {data.prices.length > 1 && (
            <View style={styles.card}>
              <Text style={styles.cardTitle}>
                {byMeasure ? `Preço por ${unit.toLowerCase()} em cada compra` : 'Preço por compra'}
              </Text>
              <ColumnChart
                data={data.prices.map((point, index) => ({
                  key: String(index),
                  label: formatDate(point.issuedAt).slice(0, 5),
                  accessibilityLabel: `${formatDate(point.issuedAt)} no ${point.storeName}`,
                  value: point.unitPrice,
                }))}
                highlightKey={String(data.prices.length - 1)}
                formatValue={value => formatUnitPrice(value, unit)}
              />
            </View>
          )}

          <View style={styles.card}>
            <Text style={styles.cardTitle}>Compras</Text>
            {[...data.prices].reverse().map((point, index) => (
              <View key={`${point.issuedAt}-${index}`} style={styles.row}>
                <View style={styles.rowMain}>
                  <Text style={styles.text}>{point.storeName}</Text>
                  <Text style={styles.muted}>{formatDate(point.issuedAt)}</Text>
                  {isSoldByMeasure(point.unit) && <Text style={styles.muted}>{amount(point)}</Text>}
                </View>
                <Text style={styles.value}>{formatCurrency(paid(point))}</Text>
              </View>
            ))}
          </View>
        </ScrollView>
      )}
    </View>
  );
}

/** What was paid on that line; older servers only sent the unit price. */
function paid(point: PricePoint): number {
  return point.totalPrice ?? point.unitPrice;
}

/** "0,148 kg · R$ 21,90/kg" */
function amount(point: PricePoint): string {
  const perUnit = formatUnitPrice(point.unitPrice, point.unit);
  return point.quantity === undefined ? perUnit : `${formatQuantity(point.quantity, point.unit)} · ${perUnit}`;
}

function where(point: PricePoint): string {
  return `${point.storeName} · ${formatDate(point.issuedAt)}`;
}

function Tile({ label, value, note }: { label: string; value: string; note: string }) {
  const styles = useStyles();
  return (
    <View style={[styles.card, styles.tile]}>
      <Text style={styles.muted}>{label}</Text>
      <Text style={styles.tileValue}>{value}</Text>
      <Text style={styles.muted}>{note}</Text>
    </View>
  );
}

const useStyles = makeStyles(colors => ({
  container: { flex: 1, backgroundColor: colors.surface },
  loading: { marginTop: spacing.xl },
  content: { padding: spacing.md, gap: spacing.md },
  card: { backgroundColor: colors.background, borderRadius: 12, padding: spacing.md, gap: spacing.xs },
  name: { fontSize: 18, fontWeight: '700', color: colors.text },
  cardTitle: { fontSize: 16, fontWeight: '700', color: colors.text, marginBottom: spacing.sm },
  tiles: { flexDirection: 'row', gap: spacing.md },
  tile: { flex: 1 },
  tileValue: { fontSize: 22, fontWeight: '700', color: colors.text },
  text: { fontSize: 15, color: colors.text },
  muted: { fontSize: 13, color: colors.textMuted },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: spacing.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
  },
  rowMain: { flex: 1, marginRight: spacing.md },
  value: { fontSize: 15, fontWeight: '700', color: colors.text },
}));
