import { useRouter } from 'expo-router';
import { useEffect, useState, type ReactNode } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '../../../api/messages';
import type {
  BestDayInsight,
  InflationInsight,
  InsightSummary,
  SavingsInsight,
  SpendingInsight,
} from '../../../api/types';
import { BudgetCard } from '../../../budget/BudgetCard';
import { formatCurrency, formatMonthLong, formatMonthShort, formatPercent } from '../../../format';
import {
  DEFAULT_PERIOD,
  loadPeriod,
  PERIODS,
  periodDescription,
  savePeriod,
  type Period,
} from '../../../insights/period';
import { monthComparisonText } from '../../../insights/comparison';
import { useBestDay, useInflation, useSavings, useSpending, useSummary } from '../../../insights/queries';
import { BarList, ColumnChart } from '../../../ui/charts';
import { Button, ErrorBanner } from '../../../ui/components';
import { makeStyles, spacing, useColors } from '../../../ui/theme';

export default function DashboardScreen() {
  const colors = useColors();
  const styles = useStyles();
  const router = useRouter();
  const [period, setPeriod] = useState<Period>(DEFAULT_PERIOD);
  useEffect(() => {
    loadPeriod().then(setPeriod);
  }, []);
  const choosePeriod = (next: Period) => {
    setPeriod(next);
    void savePeriod(next);
  };

  const spending = useSpending(period);
  const savings = useSavings(period);
  const bestDay = useBestDay(period);
  const inflation = useInflation();
  // Optional AI tips: never block the dashboard or show their errors
  const summary = useSummary();

  const queries = [spending, savings, bestDay, inflation, summary];
  const refreshing = queries.some(query => query.isRefetching);
  const refresh = () => Promise.all(queries.map(query => query.refetch()));
  const error = spending.error ?? savings.error ?? bestDay.error ?? inflation.error;
  const hasPurchases = spending.data?.monthly.some(month => month.receiptCount > 0) ?? false;

  return (
    <View style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        <PeriodSelector value={period} onChange={choosePeriod} />
        {error && <ErrorBanner message={errorMessage(error)} />}

        {spending.isPending ? (
          <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
        ) : spending.data && !hasPurchases ? (
          <EmptyDashboard />
        ) : (
          spending.data && (
            <>
              <MonthHero spending={spending.data} />
              {summary.data && <TipsCard summary={summary.data} />}
              <BudgetCard />
              <View style={styles.tiles}>
                <SavingsTile savings={savings.data} period={period} />
                <BestTimeTile bestDay={bestDay.data} />
              </View>
              {inflation.data && <InflationCard inflation={inflation.data} />}
              <Card title="Gastos por mês" subtitle={periodDescription(period)}>
                <ColumnChart
                  data={spending.data.monthly.map(month => ({
                    key: month.month,
                    label: formatMonthShort(month.month),
                    accessibilityLabel: formatMonthLong(month.month),
                    value: month.total,
                  }))}
                  highlightKey={spending.data.currentMonth.month}
                  formatValue={formatCurrency}
                />
              </Card>
              <Card title="Por categoria" subtitle={`${periodDescription(period)} · toque para ver os produtos`}>
                <BarList
                  items={spending.data.byCategory.map(c => ({ key: c.category ?? 'none', label: c.label, value: c.total }))}
                  formatValue={formatCurrency}
                  onPressItem={category =>
                    router.push({ pathname: '/categories/[category]', params: { category, months: String(period) } })
                  }
                />
              </Card>
              <Card title="Por mercado" subtitle={periodDescription(period)}>
                <BarList
                  items={spending.data.byStore.map(s => ({ key: String(s.storeId), label: s.storeName, value: s.total }))}
                  formatValue={formatCurrency}
                />
              </Card>
              {savings.data && savings.data.products.length > 0 && (
                <OverpaidList savings={savings.data} period={period} />
              )}
            </>
          )
        )}
      </ScrollView>
      <View style={styles.footer}>
        <Button title="Escanear nota" onPress={() => router.push('/scan')} />
      </View>
    </View>
  );
}

function MonthHero({ spending }: { spending: SpendingInsight }) {
  const styles = useStyles();
  const { currentMonth } = spending;
  // Same days of the previous month, so a month in progress is compared fairly
  const comparison = monthComparisonText(spending);

  return (
    <View style={styles.card}>
      <Text style={styles.cardLabel}>Gasto em {formatMonthLong(currentMonth.month)}</Text>
      <Text style={styles.hero} accessibilityRole="header">
        {formatCurrency(currentMonth.total)}
      </Text>
      <Text style={styles.muted}>
        {comparison} · {currentMonth.receiptCount} {currentMonth.receiptCount === 1 ? 'nota' : 'notas'}
      </Text>
    </View>
  );
}

function TipsCard({ summary }: { summary: InsightSummary }) {
  const styles = useStyles();
  if (!summary.available || summary.tips.length === 0) {
    return null;
  }
  return (
    <Card title="Dicas para você" subtitle="Geradas por IA a partir das suas notas">
      {summary.tips.map(tip => (
        <View key={tip} style={styles.tipRow}>
          <Text style={styles.tipBullet}>•</Text>
          <Text style={styles.tipText}>{tip}</Text>
        </View>
      ))}
    </Card>
  );
}

function InflationCard({ inflation }: { inflation: InflationInsight }) {
  const styles = useStyles();
  const latest = inflation.monthly[inflation.monthly.length - 1];
  if (!latest || latest.changePercent === null) {
    // Keep the card visible so people learn the feature exists and what it needs
    return (
      <Card title="Sua inflação" subtitle="Comparado ao mês anterior">
        <Text style={styles.muted}>
          Compre os mesmos produtos em meses seguidos para ver sua inflação: comparamos o preço de cada
          produto com o do mês anterior.
        </Text>
      </Card>
    );
  }
  const change = latest.changePercent;
  const direction = change > 0 ? '▲' : change < 0 ? '▼' : '=';
  const increases = inflation.changes.filter(product => product.changePercent > 0).slice(0, 3);

  return (
    <Card title="Sua inflação" subtitle={`${formatMonthLong(latest.month)}, comparado ao mês anterior`}>
      <Text style={styles.tileValue}>
        {direction} {formatPercent(change)} {change > 0 ? 'mais caro' : change < 0 ? 'mais barato' : 'sem variação'}
      </Text>
      <Text style={[styles.muted, styles.inflationBasis]}>
        {latest.productsCompared} {latest.productsCompared === 1 ? 'produto comparado' : 'produtos comparados'}
      </Text>
      {increases.map(product => (
        <View key={product.productId} style={styles.overpaidRow}>
          <View style={styles.overpaidMain}>
            <Text style={styles.overpaidName} numberOfLines={1}>
              {product.name}
            </Text>
            <Text style={styles.muted}>
              {formatCurrency(product.previousPrice)} → {formatCurrency(product.currentPrice)}
            </Text>
          </View>
          <Text style={styles.overpaidValue}>▲ {formatPercent(product.changePercent)}</Text>
        </View>
      ))}
    </Card>
  );
}

function PeriodSelector({ value, onChange }: { value: Period; onChange: (period: Period) => void }) {
  const styles = useStyles();
  return (
    <View style={styles.periods} accessibilityRole="tablist">
      {PERIODS.map(option => {
        const selected = option.value === value;
        return (
          <Pressable
            key={option.value}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            onPress={() => onChange(option.value)}
            style={[styles.period, selected && styles.periodSelected]}>
            <Text
              style={[styles.periodText, selected && styles.periodTextSelected]}
              numberOfLines={1}
              adjustsFontSizeToFit
              minimumFontScale={0.7}>
              {option.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

function SavingsTile({ savings, period }: { savings: SavingsInsight | undefined; period: Period }) {
  const styles = useStyles();
  return (
    <View style={[styles.card, styles.tile]}>
      <Text style={styles.cardLabel}>Economia possível</Text>
      <Text style={styles.tileValue}>{savings ? formatCurrency(savings.potentialSavings) : '…'}</Text>
      <Text style={styles.muted}>
        {savings && savings.potentialSavings > 0
          ? `${periodDescription(period).toLowerCase()}, pagando o menor preço visto até 2 meses antes ou depois`
          : 'Compre os mesmos produtos mais vezes para comparar preços'}
      </Text>
    </View>
  );
}

function BestTimeTile({ bestDay }: { bestDay: BestDayInsight | undefined }) {
  const styles = useStyles();
  const best = bestDay?.bestPeriod;
  return (
    <View style={[styles.card, styles.tile]}>
      <Text style={styles.cardLabel}>Melhor época</Text>
      <Text style={styles.tileValue}>{bestDay ? (best ? best.label : 'Em breve') : '…'}</Text>
      <Text style={styles.muted}>
        {best
          ? best.percentVsAverage < 0
            ? `preços ${formatPercent(best.percentVsAverage)} abaixo da sua média`
            : 'seus preços variam pouco ao longo do mês'
          : 'Precisamos de mais notas ao longo do mês para descobrir'}
      </Text>
      {bestDay?.bestWeekday && bestDay.bestWeekday.percentVsAverage < 0 && (
        <Text style={styles.muted}>Melhor dia: {bestDay.bestWeekday.label}</Text>
      )}
    </View>
  );
}

function OverpaidList({ savings, period }: { savings: SavingsInsight; period: Period }) {
  const styles = useStyles();
  const router = useRouter();
  return (
    <Card title="Onde você pagou mais caro" subtitle={periodDescription(period)}>
      {savings.products.slice(0, 5).map(product => (
        <Pressable
          key={product.productId}
          onPress={() => router.push({ pathname: '/products/[id]', params: { id: String(product.productId) } })}
          accessibilityRole="button"
          accessibilityHint="Mostra o histórico de preços"
          style={({ pressed }) => [styles.overpaidRow, pressed && styles.pressed]}>
          <View style={styles.overpaidMain}>
            <Text style={styles.overpaidName} numberOfLines={1}>
              {product.name}
            </Text>
            <Text style={styles.muted}>
              Melhor preço {formatCurrency(product.bestUnitPrice)} no {product.bestStoreName}
            </Text>
          </View>
          <Text style={styles.overpaidValue}>+{formatCurrency(product.extraPaid)}  ›</Text>
        </Pressable>
      ))}
    </Card>
  );
}

function EmptyDashboard() {
  const styles = useStyles();
  return (
    <View style={[styles.card, styles.empty]}>
      <Text style={styles.emptyTitle}>Seu resumo aparece aqui</Text>
      <Text style={styles.emptyText}>
        Escaneie suas notas fiscais para ver quanto você gasta, onde é mais barato e o melhor dia para comprar.
      </Text>
    </View>
  );
}

function Card({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  const styles = useStyles();
  return (
    <View style={styles.card}>
      <Text style={styles.cardTitle}>{title}</Text>
      {subtitle && <Text style={[styles.muted, styles.cardSubtitle]}>{subtitle}</Text>}
      {children}
    </View>
  );
}

const useStyles = makeStyles(colors => ({
  container: { flex: 1, backgroundColor: colors.surface },
  content: { padding: spacing.md, gap: spacing.md },
  loading: { marginTop: spacing.xl },
  card: { backgroundColor: colors.background, borderRadius: 12, padding: spacing.md },
  cardLabel: { fontSize: 14, color: colors.textMuted },
  cardTitle: { fontSize: 16, fontWeight: '700', color: colors.text },
  cardSubtitle: { marginBottom: spacing.md },
  hero: { fontSize: 48, fontWeight: '700', color: colors.text, marginVertical: spacing.xs },
  muted: { fontSize: 13, color: colors.textMuted },
  tiles: { flexDirection: 'row', gap: spacing.md },
  periods: {
    flexDirection: 'row',
    backgroundColor: colors.chartTrack,
    borderRadius: 10,
    padding: 3,
  },
  period: {
    flex: 1,
    minHeight: 36,
    paddingHorizontal: 4,
    paddingVertical: 6,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  periodSelected: { backgroundColor: colors.background },
  periodText: { fontSize: 14, color: colors.textMuted },
  periodTextSelected: { color: colors.text, fontWeight: '600' },
  tile: { flex: 1, gap: spacing.xs },
  tileValue: { fontSize: 22, fontWeight: '700', color: colors.text },
  overpaidRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: spacing.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
  },
  pressed: { opacity: 0.6 },
  overpaidMain: { flex: 1, marginRight: spacing.md },
  overpaidName: { fontSize: 15, color: colors.text },
  overpaidValue: { fontSize: 15, fontWeight: '700', color: colors.text },
  tipRow: { flexDirection: 'row', gap: spacing.sm, paddingVertical: spacing.xs },
  tipBullet: { fontSize: 15, color: colors.primary, fontWeight: '700' },
  tipText: { flex: 1, fontSize: 15, color: colors.text, lineHeight: 21 },
  inflationBasis: { marginBottom: spacing.sm },
  empty: { alignItems: 'center', paddingVertical: spacing.xl },
  emptyTitle: { fontSize: 20, fontWeight: '700', color: colors.text, marginBottom: spacing.sm },
  emptyText: { fontSize: 15, color: colors.textMuted, textAlign: 'center' },
  footer: {
    padding: spacing.md,
    paddingBottom: spacing.lg,
    backgroundColor: colors.background,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
  },
}));
